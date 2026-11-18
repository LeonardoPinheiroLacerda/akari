package io.github.leonardopinheirolacerda.akari.domain.torrent.model;

import io.github.leonardopinheirolacerda.akari.domain.torrent.provider.model.ScrapedRelease;

/**
 * Release lida do provedor com o título já interpretado.
 *
 * @param scrapedRelease release como lida do provedor
 * @param episode número do episódio; {@code null} em batches e extras
 * @param version versão da release ({@code 1} sem versão no título)
 * @param group grupo de fansub; {@code null} sem grupo
 * @param resolution resolução; {@code null} se o título não traz
 * @param editionKey chave da edição a que a release pertence
 * @param editionLabel rótulo legível da edição
 */
public record ParsedRelease(
        ScrapedRelease scrapedRelease,
        Integer episode,
        Integer version,
        String group,
        String resolution,
        String editionKey,
        String editionLabel
) {
}
