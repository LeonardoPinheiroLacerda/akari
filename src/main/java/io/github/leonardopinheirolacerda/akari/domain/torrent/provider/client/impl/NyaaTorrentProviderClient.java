package io.github.leonardopinheirolacerda.akari.domain.torrent.provider.client.impl;

import io.github.leonardopinheirolacerda.akari.api.dto.TorrentProviderType;
import io.github.leonardopinheirolacerda.akari.domain.torrent.provider.client.ScrapingTorrentProviderClient;
import io.github.leonardopinheirolacerda.akari.domain.torrent.provider.model.ScrapedRelease;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link ScrapingTorrentProviderClient} do nyaa.si, lido pela página de busca HTML.
 *
 * <p>A busca HTML, e não o RSS, porque o RSS do nyaa só devolve os ~75 resultados mais recentes
 * e ignora paginação. Busca só na categoria {@code 1_2} (anime traduzido para o inglês), sem
 * filtro de uploader, ordenada pelo id da release, da mais nova para a mais antiga.
 */
public class NyaaTorrentProviderClient extends ScrapingTorrentProviderClient {

    private static final String BASE_URL = "https://nyaa.si";

    private static final int PAGE_SIZE = 75;

    // O nyaa nunca entrega mais que 1000 resultados: da página 14 em diante repete a última.
    private static final int MAX_RESULTS = 1000;

    private static final Pattern TOTAL_RESULTS = Pattern.compile("out of (\\d+) results");

    // Colunas da tabela de resultados: categoria, nome, links, tamanho, data, seeders, leechers, downloads.
    private static final int SIZE_COLUMN = 3;

    private static final int DATE_COLUMN = 4;

    private static final int SEEDERS_COLUMN = 5;

    private static final Pattern INFO_HASH = Pattern.compile("btih:([0-9a-fA-F]{40})");

    private static final Pattern DIGITS = Pattern.compile("\\d+");

    /**
     * Cria o client do nyaa.
     *
     * @param httpClient client HTTP que sai pelo proxy da VPN
     */
    public NyaaTorrentProviderClient(HttpClient httpClient) {
        super(httpClient);
    }

    /**
     * Identifica o provedor como {@code NYAA}.
     *
     * @return {@link TorrentProviderType#NYAA}
     */
    @Override
    public TorrentProviderType type() {
        return TorrentProviderType.NYAA;
    }

    /**
     * Monta a URL de uma página da busca HTML do nyaa.
     *
     * @param query texto de busca
     * @param page número da página, a partir de 1
     * @return a URL da página
     */
    @Override
    protected URI pageUri(String query, Integer page) {
        final String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);

        return URI.create("%s/?q=%s&c=1_2&f=0&s=id&o=desc&p=%d".formatted(BASE_URL, encodedQuery, page));
    }

    /**
     * Tamanho de uma página cheia de resultados no nyaa.
     *
     * @return 75
     */
    @Override
    protected Integer pageSize() {
        return PAGE_SIZE;
    }

    /**
     * Limite de resultados de uma busca no nyaa.
     *
     * @return 1000
     */
    @Override
    protected Integer maxResults() {
        return MAX_RESULTS;
    }

    /**
     * Lê o total de resultados do rodapé da página ({@code "Displaying results 1-75 out of 861
     * results."}).
     *
     * @param firstPage HTML da primeira página de resultados
     * @return o total informado pelo nyaa; {@code 0} se o rodapé não trouxer o total
     */
    @Override
    protected Integer totalResults(Document firstPage) {
        final Matcher totalResults = TOTAL_RESULTS.matcher(firstPage.select(".pagination-page-info").text());

        if (!totalResults.find()) {
            return 0;
        }
        return Integer.valueOf(totalResults.group(1));
    }

    /**
     * Lê as linhas da tabela de resultados ({@code .torrent-list}).
     *
     * @param document HTML da página de busca
     * @return uma release por linha com link de magnet; linhas sem info-hash ficam de fora
     */
    @Override
    protected List<ScrapedRelease> parsePage(Document document) {
        final List<ScrapedRelease> releases = new ArrayList<>();

        for (Element row : document.select(".torrent-list tbody tr")) {
            final Element nameLink = row.selectFirst("a[href^=\"/view/\"]:not(.comments)");
            final Element magnetLink = row.selectFirst("a[href^=\"magnet:\"]");
            final Elements cells = row.select("td");

            if (nameLink == null || magnetLink == null || cells.size() <= SEEDERS_COLUMN) {
                continue;
            }

            final String magnetUri = magnetLink.attr("href");
            final Matcher infoHash = INFO_HASH.matcher(magnetUri);

            if (!infoHash.find()) {
                continue;
            }

            releases.add(new ScrapedRelease(
                    infoHash.group(1).toLowerCase(Locale.ROOT),
                    magnetUri,
                    titleOf(nameLink),
                    cells.get(SIZE_COLUMN).text(),
                    seedersOf(cells.get(SEEDERS_COLUMN)),
                    publishedAtOf(cells.get(DATE_COLUMN)),
                    BASE_URL + nameLink.attr("href")
            ));
        }

        return releases;
    }

    private String titleOf(Element nameLink) {
        final String title = nameLink.attr("title");

        if (title.isBlank()) {
            return nameLink.text();
        }
        return title;
    }

    private Integer seedersOf(Element cell) {
        final String seeders = cell.text();

        if (!DIGITS.matcher(seeders).matches()) {
            return null;
        }
        return Integer.valueOf(seeders);
    }

    private OffsetDateTime publishedAtOf(Element cell) {
        final String timestamp = cell.attr("data-timestamp");

        if (!DIGITS.matcher(timestamp).matches()) {
            return null;
        }
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(timestamp)), ZoneOffset.UTC);
    }
}
