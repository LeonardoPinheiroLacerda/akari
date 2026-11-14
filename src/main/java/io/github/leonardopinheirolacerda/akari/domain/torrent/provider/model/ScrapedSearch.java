package io.github.leonardopinheirolacerda.akari.domain.torrent.provider.model;

import java.util.List;

/**
 * Resultado de uma busca num provedor de torrent, com todas as páginas lidas.
 *
 * @param releases releases de todas as páginas, na ordem do provedor (da mais nova para a mais
 *                 antiga)
 * @param truncated {@code true} quando o provedor cortou a busca no limite de resultados dele e
 *                  as releases mais antigas ficaram de fora
 */
public record ScrapedSearch(
        List<ScrapedRelease> releases,
        boolean truncated
) {
}
