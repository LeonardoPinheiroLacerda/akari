package io.github.leonardopinheirolacerda.akari.domain.torrent.resource;

import io.github.leonardopinheirolacerda.akari.api.TorrentSearchApi;
import io.github.leonardopinheirolacerda.akari.api.dto.TorrentProviderType;
import io.github.leonardopinheirolacerda.akari.api.dto.TorrentSearchResult;
import io.github.leonardopinheirolacerda.akari.domain.torrent.service.TorrentSearchService;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.exceptions.RateLimitedException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Implementa {@link TorrentSearchApi} — busca de releases nos provedores de torrent, em
 * {@code /v1/torrent/search}.
 *
 * <p>Só traduz HTTP ↔ service, sem lógica própria: o scrape e o agrupamento por edição ficam no
 * {@link TorrentSearchService}.
 */
@ApplicationScoped
public class TorrentSearchResource implements TorrentSearchApi {

    @Inject
    TorrentSearchService torrentSearchService;

    /**
     * {@code GET /v1/torrent/search} — busca releases num provedor, agrupadas por edição.
     *
     * @param provider provedor onde buscar
     * @param q textos de busca; as releases de todos são juntadas
     * @return as edições encontradas e se o provedor cortou a busca, com {@code 200 OK}; busca
     *         sem resultado devolve nenhuma edição
     * @throws RateLimitedException (traduzida em 429) se o provedor recusar por excesso de
     *         requisições
     * @throws IntegrationException (traduzida em 502) se o provedor ou o proxy da VPN não
     *         responder
     */
    @Override
    public TorrentSearchResult searchTorrentReleases(TorrentProviderType provider, List<String> q) {
        return torrentSearchService.search(provider, q);
    }
}
