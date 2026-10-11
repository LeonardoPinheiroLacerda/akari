package io.github.leonardopinheirolacerda.akari.domain.ingestion.service;

import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeRelationsService;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Calcula o deslocamento de numeração absoluta de um anime a partir da cadeia de prequels da
 * AniList — quando uma temporada continua a contagem de episódios da anterior em vez de
 * reiniciar em 1 (ex.: arquivos "14-38" de uma 2ª temporada cujo mapeamento do anibridge é
 * relativo, "1-25"). Fica em {@code ingestion}, não em {@code anime}, de propósito: é regra de
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
     * Soma o {@code episodes} de cada prequel direto de formato {@code TV} na cadeia, andando
     * pra trás até não haver mais prequel ou até a cadeia sair do formato {@code TV}/episódios
     * desconhecidos — nesse ponto para, sem pular pra trás do que não sabe somar.
     *
     * @param anilistId anime cujo offset se quer calcular
     * @return a soma de episódios das temporadas anteriores, ou {@code 0} se não houver prequel
     *         conhecido (ainda não ingerido, ou chegou no início da franquia)
     */
    @Transactional
    public int computeOffset(Integer anilistId) {
        int offset = 0;
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

            offset += prequel.get().episodes;
            currentId = prequelId.get();
        }

        return offset;
    }

}
