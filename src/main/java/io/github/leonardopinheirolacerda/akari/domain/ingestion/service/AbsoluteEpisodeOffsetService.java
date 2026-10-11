package io.github.leonardopinheirolacerda.akari.domain.ingestion.service;

import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeRelationsService;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Calcula os deslocamentos candidatos de numeração absoluta de um anime a partir da cadeia de
 * prequels da AniList — quando uma temporada continua a contagem de episódios da anterior em
 * vez de reiniciar em 1 (ex.: arquivos "14-38" de uma 2ª temporada cujo mapeamento do anibridge
 * é relativo, "1-25"). Fica em {@code ingestion}, não em {@code anime}, de propósito: é regra de
 * consumo específica do pairing TMDB, não do domínio de relações em si.
 */
@ApplicationScoped
public class AbsoluteEpisodeOffsetService {

    private static final String TV_FORMAT = "TV";

    @Inject
    AnimeService animeService;

    @Inject
    AnimeRelationsService animeRelationsService;

    /**
     * Soma cumulativa do {@code episodes} de cada prequel direto de formato {@code TV} na
     * cadeia, um candidato por nível andado — do mais próximo (1 nível) ao mais distante (cadeia
     * inteira). Não existe um número fixo de níveis certo: a convenção de numeração absoluta
     * varia por fansub — algumas reiniciam a contagem por temporada (o offset certo é só o nível
     * mais próximo), outras continuam pela franquia inteira (o offset certo soma a cadeia toda).
     * Quem consome tenta cada candidato, do mais próximo ao mais distante, e usa o primeiro que
     * resolver o episódio de verdade — nunca confia ciegamente na soma completa.
     *
     * @param anilistId anime cujos candidatos de offset se quer calcular
     * @return os offsets candidatos, em ordem crescente de distância na cadeia; vazio se não
     *         houver prequel conhecido (ainda não ingerido, ou chegou no início da franquia)
     */
    @Transactional
    public List<Integer> computeOffsetCandidates(Integer anilistId) {
        final List<Integer> candidates = new ArrayList<>();
        int cumulative = 0;
        Integer currentId = anilistId;
        final Set<Integer> visited = new HashSet<>();

        while (currentId != null && visited.add(currentId)) {
            final Optional<Integer> prequelId = animeRelationsService.findDirectPrequelId(currentId);

            if (prequelId.isEmpty()) {
                break;
            }

            final Optional<Anime> prequel = animeService.find(prequelId.get());

            if (prequel.isEmpty() || !TV_FORMAT.equals(prequel.get().format) || prequel.get().episodes == null) {
                break;
            }

            cumulative += prequel.get().episodes;
            candidates.add(cumulative);
            currentId = prequelId.get();
        }

        return candidates;
    }

}
