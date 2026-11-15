package io.github.leonardopinheirolacerda.akari.domain.torrent.resource;

import io.github.leonardopinheirolacerda.akari.api.TorrentTrackingApi;
import io.github.leonardopinheirolacerda.akari.api.dto.TorrentEdition;
import io.github.leonardopinheirolacerda.akari.api.dto.TrackedAnimeQueriesRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.TrackedAnimeRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.TrackedAnimeResponse;
import io.github.leonardopinheirolacerda.akari.domain.torrent.service.TorrentTrackingService;
import io.github.leonardopinheirolacerda.akari.exceptions.BusinessRuleException;
import io.github.leonardopinheirolacerda.akari.exceptions.ConflictException;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationNotConfiguredException;
import io.github.leonardopinheirolacerda.akari.exceptions.RateLimitedException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Implementa {@link TorrentTrackingApi} — rastreio de animes em lançamento nos provedores de
 * torrent, em {@code /v1/torrent/tracked}.
 *
 * <p>Só traduz HTTP ↔ service, sem lógica própria: tudo fica no {@link TorrentTrackingService}.
 */
@ApplicationScoped
public class TorrentTrackingResource implements TorrentTrackingApi {

    @Inject
    TorrentTrackingService torrentTrackingService;

    /**
     * {@code GET /v1/torrent/tracked} — lista os animes rastreados.
     *
     * @return os rastreios, do mais recente para o mais antigo, com {@code 200 OK}
     */
    @Override
    public List<TrackedAnimeResponse> listTrackedAnimes() {
        return torrentTrackingService.listTracked();
    }

    /**
     * {@code POST /v1/torrent/tracked} — começa a rastrear um anime.
     *
     * @param trackedAnimeRequest anime, provedor, queries e edição a rastrear
     * @return o rastreio criado, com {@code 201 Created}
     * @throws BusinessRuleException (traduzida em 400) se a edição não aparecer na busca ou a
     *         biblioteca não for {@code LOCAL}
     * @throws ResourceNotFoundException (traduzida em 404) se o anime não existir na AniList
     * @throws ConflictException (traduzida em 409) se o anime já é rastreado ou está vinculado a
     *         uma pasta de outro storage provider
     * @throws RateLimitedException (traduzida em 429) se o provedor recusar por excesso de
     *         requisições
     * @throws IntegrationException (traduzida em 502) se o provedor ou a AniList falharem
     * @throws IntegrationNotConfiguredException (traduzida em 503) se a biblioteca não estiver
     *         configurada
     */
    @Override
    public TrackedAnimeResponse trackAnime(TrackedAnimeRequest trackedAnimeRequest) {
        return torrentTrackingService.track(trackedAnimeRequest);
    }

    /**
     * {@code PUT /v1/torrent/tracked/{trackedId}/queries} — troca as queries de busca de um
     * rastreio.
     *
     * @param trackedId id do rastreio
     * @param trackedAnimeQueriesRequest as novas queries
     * @return o rastreio atualizado, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se o rastreio não existir
     */
    @Override
    public TrackedAnimeResponse updateTrackedAnimeQueries(Integer trackedId, TrackedAnimeQueriesRequest trackedAnimeQueriesRequest) {
        return torrentTrackingService.updateQueries(trackedId, trackedAnimeQueriesRequest);
    }

    /**
     * {@code DELETE /v1/torrent/tracked/{trackedId}} — para de rastrear um anime.
     *
     * @param trackedId id do rastreio
     */
    @Override
    public void untrackAnime(Integer trackedId) {
        torrentTrackingService.untrack(trackedId);
    }

    /**
     * {@code GET /v1/torrent/tracked/{trackedId}/releases} — releases da edição rastreada.
     *
     * @param trackedId id do rastreio
     * @return a edição rastreada, com o estado de download de cada release, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se o rastreio não existir
     * @throws RateLimitedException (traduzida em 429) se o provedor recusar por excesso de
     *         requisições
     * @throws IntegrationException (traduzida em 502) se o provedor não responder
     */
    @Override
    public TorrentEdition listTrackedAnimeReleases(Integer trackedId) {
        return torrentTrackingService.listReleases(trackedId);
    }
}
