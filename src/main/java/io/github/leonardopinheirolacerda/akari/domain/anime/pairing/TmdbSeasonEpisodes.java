package io.github.leonardopinheirolacerda.akari.domain.anime.pairing;

import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbEpisodeResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbSeasonSummaryResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.service.TmdbService;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.github.leonardopinheirolacerda.akari.utils.RetryUtils;
import io.quarkus.logging.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Temporadas e episódios da série do TMDB vinculada ao anime, buscados sob demanda e guardados
 * durante a resolução de uma pasta — as etapas que consultam o TMDB ({@link TmdbEpisodeValidator},
 * {@link AirDateEpisodeMapper}) compartilham a mesma busca, sem repetir a mesma temporada a cada
 * arquivo. Falha na busca, mesmo depois do retry de rate limit, vira lista vazia, com log: o
 * arquivo fica sem pairing em vez de derrubar a resolução.
 */
public final class TmdbSeasonEpisodes {

    private final TmdbService tmdbService;

    private final Integer tmdbId;

    private final Integer folderId;

    private final String folderName;

    private final Map<Integer, List<TmdbEpisodeResponse>> episodesBySeason = new HashMap<>();

    /**
     * @param tmdbService busca no TMDB, cache-first
     * @param tmdbId série do TMDB vinculada ao anime
     * @param folderId pasta resolvida, só pra log
     * @param folderName nome da pasta, só pra log
     */
    public TmdbSeasonEpisodes(TmdbService tmdbService,
                              Integer tmdbId,
                              Integer folderId,
                              String folderName) {

        this.tmdbService = tmdbService;
        this.tmdbId = tmdbId;
        this.folderId = folderId;
        this.folderName = folderName;
    }

    /**
     * Números das temporadas da série, sem a temporada 0 (especiais), em ordem crescente.
     *
     * @return as temporadas; vazia se a série não existir mais no TMDB ou a busca falhar
     */
    public List<Integer> regularSeasonNumbers() {
        try {
            return RetryUtils.withRateLimitRetry(() -> tmdbService.getTvDetails(tmdbId, false))
                    .seasons()
                    .stream()
                    .map(TmdbSeasonSummaryResponse::seasonNumber)
                    .filter(Objects::nonNull)
                    .filter(seasonNumber -> seasonNumber > 0)
                    .sorted()
                    .toList();
        } catch (IntegrationException | ResourceNotFoundException e) {
            Log.warnf(
                    e,
                    "Busca da série %d no TMDB falhou pra pasta %d (%s)",
                    tmdbId,
                    folderId,
                    folderName
            );
            return List.of();
        }
    }

    /**
     * Episódios de uma temporada.
     *
     * @param seasonNumber número da temporada
     * @return os episódios; vazia se a temporada não existir ou a busca falhar
     */
    public List<TmdbEpisodeResponse> of(Integer seasonNumber) {
        return episodesBySeason.computeIfAbsent(seasonNumber, this::fetch);
    }

    private List<TmdbEpisodeResponse> fetch(Integer seasonNumber) {
        try {
            return RetryUtils.withRateLimitRetry(
                    () -> tmdbService.getSeasonEpisodes(tmdbId, seasonNumber)
            );
        } catch (IntegrationException e) {
            Log.warnf(
                    e,
                    "Busca da temporada %d no TMDB falhou pra pasta %d (%s)",
                    seasonNumber,
                    folderId,
                    folderName
            );
            return List.of();
        }
    }

}
