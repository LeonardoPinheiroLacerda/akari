package io.github.leonardopinheirolacerda.akari.domain.torrent.resource;

import io.github.leonardopinheirolacerda.akari.api.TorrentDownloadApi;
import io.github.leonardopinheirolacerda.akari.api.dto.TorrentDownloadPage;
import io.github.leonardopinheirolacerda.akari.api.dto.TorrentDownloadRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.TorrentDownloadResponse;
import io.github.leonardopinheirolacerda.akari.domain.torrent.service.TorrentDownloadService;
import io.github.leonardopinheirolacerda.akari.exceptions.BusinessRuleException;
import io.github.leonardopinheirolacerda.akari.exceptions.ConflictException;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationNotConfiguredException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Implementa {@link TorrentDownloadApi} — downloads de releases de torrent pelo qBittorrent, em
 * {@code /v1/torrent/downloads}.
 *
 * <p>Só traduz HTTP ↔ service, sem lógica própria: tudo fica no {@link TorrentDownloadService}.
 */
@ApplicationScoped
public class TorrentDownloadResource implements TorrentDownloadApi {

    @Inject
    TorrentDownloadService torrentDownloadService;

    /**
     * {@code GET /v1/torrent/downloads} — página dos downloads em andamento e concluídos.
     *
     * @param page página desejada, 0-based
     * @param size tamanho da página
     * @return a página de downloads, com {@code 200 OK}; vazia se nada foi baixado
     */
    @Override
    public TorrentDownloadPage listTorrentDownloads(Integer page, Integer size) {
        return torrentDownloadService.listDownloads(page, size);
    }

    /**
     * {@code POST /v1/torrent/downloads} — baixa uma release para a pasta do anime na biblioteca.
     *
     * @param torrentDownloadRequest release a baixar e o anime a que ela pertence
     * @return o download, com {@code 201 Created}
     * @throws BusinessRuleException (traduzida em 400) se a biblioteca não for {@code LOCAL} ou a
     *         pasta nova já estiver vinculada a outro anime
     * @throws ResourceNotFoundException (traduzida em 404) se o provider da biblioteca ou o anime
     *         na AniList não existirem
     * @throws ConflictException (traduzida em 409) se o anime estiver vinculado a uma pasta de
     *         outro provider
     * @throws IntegrationException (traduzida em 502) se o qBittorrent ou a AniList falharem
     * @throws IntegrationNotConfiguredException (traduzida em 503) se a preferência
     *         {@code torrent.library-provider-id} estiver vazia
     */
    @Override
    public TorrentDownloadResponse startTorrentDownload(TorrentDownloadRequest torrentDownloadRequest) {
        return torrentDownloadService.startDownload(torrentDownloadRequest);
    }
}
