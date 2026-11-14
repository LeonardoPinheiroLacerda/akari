package io.github.leonardopinheirolacerda.akari.domain.torrent.provider.client;

import io.github.leonardopinheirolacerda.akari.api.dto.TorrentProviderType;
import io.github.leonardopinheirolacerda.akari.domain.torrent.provider.model.ScrapedSearch;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.exceptions.RateLimitedException;

/**
 * Operações que um provedor de torrent precisa suportar, comuns a todos os provedores.
 *
 * <p>Implementada por {@code ScrapingTorrentProviderClient} e suas subclasses, uma por site
 * (ex.: {@code NyaaTorrentProviderClient}).
 */
public interface TorrentProviderClient {

    /**
     * Identifica o provedor.
     *
     * @return o tipo do provedor que esta implementação lê
     */
    TorrentProviderType type();

    /**
     * Busca releases no provedor, lendo todas as páginas de resultado que ele oferece.
     *
     * @param query texto de busca, repassado ao provedor
     * @return as releases encontradas, da mais nova para a mais antiga, e se o provedor cortou a
     *         busca no limite de resultados dele
     * @throws RateLimitedException se o provedor recusar por excesso de requisições
     * @throws IntegrationException se o provedor (ou o proxy da VPN) não responder ou responder
     *         com erro
     */
    ScrapedSearch search(String query);
}
