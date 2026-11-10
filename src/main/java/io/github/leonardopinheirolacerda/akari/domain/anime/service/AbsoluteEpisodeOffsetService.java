package io.github.leonardopinheirolacerda.akari.domain.anime.service;

import io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos.AnilistMediaResponse;
import io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos.AnilistRelationEdgeResponse;
import io.github.leonardopinheirolacerda.akari.domain.anilist.service.AnilistSearchService;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.github.leonardopinheirolacerda.akari.utils.RetryUtils;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Calcula os deslocamentos candidatos de numeração absoluta de um anime a partir da cadeia de
 * prequels da AniList — quando uma temporada continua a contagem de episódios da anterior em
 * vez de reiniciar em 1 (ex.: arquivos "14-38" de uma 2ª temporada cujo mapeamento do anibridge
 * é relativo, "1-25"). É regra de consumo específica do pairing TMDB, não do domínio de relações
 * em si — por isso o único consumidor é o {@code AnibridgeEpisodeMapper} ({@code anime/pairing}).
 *
 * <p>Lê a cadeia do banco primeiro e cai pra AniList (cache-first) quando o prequel, ou a
 * aresta dele, ainda não está salvo — senão o resultado dependeria da ordem em que a ingestão
 * processa as pastas da franquia.
 */
@ApplicationScoped
public class AbsoluteEpisodeOffsetService {

    private static final String TV_FORMAT = "TV";

    private static final String PREQUEL_RELATION = "PREQUEL";

    private static final String ANIME_TYPE = "ANIME";

    @Inject
    AnimeService animeService;

    @Inject
    AnimeRelationsService animeRelationsService;

    @Inject
    AnilistSearchService anilistSearchService;

    /**
     * Soma cumulativa do {@code episodes} de cada prequel direto de formato {@code TV} na
     * cadeia, um candidato por nível andado — do mais próximo (1 nível) ao mais distante (cadeia
     * inteira). Não existe um número fixo de níveis certo: a convenção de numeração absoluta
     * varia por fansub — algumas reiniciam a contagem por temporada (o offset certo é só o nível
     * mais próximo), outras continuam pela franquia inteira (o offset certo soma a cadeia toda).
     * Quem consome tenta os candidatos — num arquivo isolado, o primeiro, do mais próximo ao
     * mais distante, que resolver o episódio de verdade; na pasta inteira, o que resolver mais
     * arquivos —, nunca confia cegamente na soma completa.
     *
     * <p>Sem transação própria: as leituras do banco abrem a sua, e as chamadas à AniList, com
     * retry de rate limit, não seguram transação aberta.
     *
     * @param anilistId anime cujos candidatos de offset se quer calcular
     * @return os offsets candidatos, em ordem crescente de distância na cadeia; vazio se não
     *         houver prequel (início da franquia) ou se a AniList não puder ser consultada
     */
    public List<Integer> computeOffsetCandidates(Integer anilistId) {
        final List<Integer> candidates = new ArrayList<>();
        int cumulative = 0;
        Integer currentId = anilistId;
        final Set<Integer> visited = new HashSet<>();

        while (currentId != null && visited.add(currentId)) {
            final Optional<Integer> prequelId = findPrequelId(currentId);

            if (prequelId.isEmpty()) {
                break;
            }

            final Optional<Integer> prequelEpisodes = findTvEpisodes(prequelId.get());

            if (prequelEpisodes.isEmpty()) {
                break;
            }

            cumulative += prequelEpisodes.get();
            candidates.add(cumulative);
            currentId = prequelId.get();
        }

        return candidates;
    }

    /**
     * Prequel direto do anime: a aresta {@code PREQUEL} salva no banco, ou, sem ela, a primeira
     * relação {@code PREQUEL} de anime na AniList.
     */
    private Optional<Integer> findPrequelId(Integer anilistId) {
        final Optional<Integer> storedPrequelId = animeRelationsService.findDirectPrequelId(
                anilistId
        );

        if (storedPrequelId.isPresent()) {
            return storedPrequelId;
        }

        final Optional<AnilistMediaResponse> media = fetchFromAnilist(anilistId);

        if (media.isEmpty()
                || media.get().relations() == null
                || media.get().relations().edges() == null) {

            return Optional.empty();
        }

        return media.get()
                .relations()
                .edges()
                .stream()
                .filter(edge -> PREQUEL_RELATION.equals(edge.relationType()))
                .map(AnilistRelationEdgeResponse::node)
                .filter(node -> node != null && ANIME_TYPE.equals(node.type()))
                .map(node -> node.id())
                .findFirst();
    }

    /**
     * Total de episódios do prequel, se ele for de formato {@code TV} — do anime salvo no banco,
     * ou, sem ele, da AniList. Vazio pra outro formato ou total desconhecido, o que encerra a
     * cadeia.
     */
    private Optional<Integer> findTvEpisodes(Integer anilistId) {
        final Optional<Anime> storedAnime = animeService.find(anilistId);

        if (storedAnime.isPresent()) {
            return TV_FORMAT.equals(storedAnime.get().format)
                    ? Optional.ofNullable(storedAnime.get().episodes)
                    : Optional.empty();
        }

        return fetchFromAnilist(anilistId)
                .filter(media -> TV_FORMAT.equals(media.format()))
                .map(AnilistMediaResponse::episodes);
    }

    /**
     * Ficha do anime na AniList, cache-first e com retry de rate limit. Falha na AniList encerra
     * a cadeia em vez de derrubar o pairing — sem o offset, os arquivos de numeração absoluta só
     * ficam sem par.
     */
    private Optional<AnilistMediaResponse> fetchFromAnilist(Integer anilistId) {
        try {
            return Optional.of(RetryUtils.withRateLimitRetry(
                    () -> anilistSearchService.findById(anilistId, false)
            ));
        } catch (IntegrationException | ResourceNotFoundException e) {
            Log.warnf(
                    e,
                    "Busca do anime %d na AniList falhou, cadeia de prequels interrompida",
                    anilistId
            );
            return Optional.empty();
        }
    }

}
