package io.github.leonardopinheirolacerda.akari.domain.torrent.provider.client;

import io.github.leonardopinheirolacerda.akari.domain.torrent.provider.model.ScrapedRelease;
import io.github.leonardopinheirolacerda.akari.domain.torrent.provider.model.ScrapedSearch;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.exceptions.RateLimitedException;
import io.quarkus.logging.Log;
import jakarta.ws.rs.core.HttpHeaders;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Base de {@link TorrentProviderClient} para provedores lidos por webscrap: concentra o acesso
 * HTTP, a paginação da busca e o tratamento das respostas de erro. Cada subclasse só informa a
 * URL de uma página de busca, o tamanho de página, o total de resultados e o limite de
 * resultados do site, e como ler as releases do HTML.
 *
 * <p>A primeira página é lida sozinha, para saber o total de resultados; as demais, até a última
 * que o provedor oferece, em paralelo, no máximo {@value #MAX_CONCURRENT_REQUESTS} ao mesmo tempo.
 * {@link #search} é {@code synchronized} para que duas buscas no mesmo provedor não somem
 * requests simultâneos além desse limite — por isso cada provedor tem uma única instância (ver
 * {@code TorrentProviderClientFactory}).
 *
 * <p>O {@link HttpClient} recebido sai pelo proxy HTTP do gluetun, sem fallback para a conexão
 * direta.
 */
@RequiredArgsConstructor
public abstract class ScrapingTorrentProviderClient implements TorrentProviderClient {

    private static final int MAX_CONCURRENT_REQUESTS = 4;

    private static final int TOO_MANY_REQUESTS = 429;

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private static final String USER_AGENT = "akari";

    private static final Pattern SECONDS = Pattern.compile("\\d+");

    private final HttpClient httpClient;

    /**
     * Busca releases lendo todas as páginas de resultado que o provedor oferece.
     *
     * @param query texto de busca
     * @return as releases de todas as páginas, na ordem do provedor, e se o provedor cortou a
     *         busca no limite dele
     * @throws RateLimitedException se o provedor responder {@code 429} em alguma página
     * @throws IntegrationException se alguma página não puder ser lida
     */
    @Override
    public synchronized ScrapedSearch search(String query) {
        final Document firstPage = fetch(pageUri(query, 1));
        final Integer totalResults = totalResults(firstPage);
        final Integer availableResults = Math.min(totalResults, maxResults());
        final Integer lastPage = Math.ceilDiv(availableResults, pageSize());

        Log.infof("Buscando \"%s\" no provedor de torrent %s: %d resultados, %d páginas", query, type(), totalResults, lastPage);

        final List<ScrapedRelease> releases = new ArrayList<>(parsePage(firstPage));

        final List<ScrapedRelease> pages = fetchRemainingPages(query, lastPage);
        releases.addAll(pages);

        return new ScrapedSearch(releases, totalResults >= maxResults());
    }

    /**
     * Monta a URL de uma página de resultados da busca no provedor.
     *
     * @param query texto de busca, ainda sem codificação
     * @param page número da página, a partir de 1
     * @return a URL da página, ordenada da release mais nova para a mais antiga
     */
    protected abstract URI pageUri(String query, Integer page);

    /**
     * Quantas releases uma página cheia de resultados traz no provedor.
     *
     * @return o tamanho de página do provedor
     */
    protected abstract Integer pageSize();

    /**
     * Quantos resultados o provedor entrega no máximo numa busca; o que passa disso não aparece
     * em nenhuma página.
     *
     * @return o limite de resultados do provedor
     */
    protected abstract Integer maxResults();

    /**
     * Lê o total de resultados da busca, informado na primeira página.
     *
     * @param firstPage HTML da primeira página de resultados
     * @return o total de resultados, que pode passar de {@link #maxResults()}; {@code 0} sem
     *         resultados
     */
    protected abstract Integer totalResults(Document firstPage);

    /**
     * Lê as releases de uma página de resultados.
     *
     * @param document HTML da página
     * @return as releases da página, na ordem em que aparecem; linhas sem info-hash ficam de fora
     */
    protected abstract List<ScrapedRelease> parsePage(Document document);

    private List<ScrapedRelease> fetchRemainingPages(String query, Integer lastPage) {
        final List<ScrapedRelease> releases = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(MAX_CONCURRENT_REQUESTS)) {
            final List<Future<List<ScrapedRelease>>> pages = IntStream
                    .rangeClosed(2, lastPage)
                    .mapToObj(page -> executor.submit(() -> parsePage(fetch(pageUri(query, page)))))
                    .toList();

            for (Future<List<ScrapedRelease>> page : pages) {
                releases.addAll(page.get());
            }
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw new IntegrationException("Falha ao buscar no provedor de torrent %s".formatted(type()), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new IntegrationException("Busca no provedor de torrent %s interrompida".formatted(type()), e);
        }

        return releases;
    }

    private Document fetch(URI uri) {
        final HttpRequest request = HttpRequest
                .newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .GET()
                .build();

        final HttpResponse<String> response = send(request);

        if (response.statusCode() == TOO_MANY_REQUESTS) {
            throw new RateLimitedException(rateLimitMessage(response));
        }

        if (response.statusCode() != HttpURLConnection.HTTP_OK) {
            throw new IntegrationException(
                    "O provedor de torrent %s respondeu HTTP %d".formatted(type(), response.statusCode())
            );
        }

        return Jsoup.parse(response.body(), uri.toString());
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IntegrationException(
                    "Falha ao acessar o provedor de torrent %s pelo proxy da VPN".formatted(type()),
                    e
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new IntegrationException(
                    "Busca no provedor de torrent %s interrompida".formatted(type()),
                    e
            );
        }
    }

    private String rateLimitMessage(HttpResponse<String> response) {
        final String refused = "O provedor de torrent %s recusou a busca por excesso de requisições (limite de uso do site)."
                .formatted(type());

        final Optional<String> retryAfter = response
                .headers()
                .firstValue(HttpHeaders.RETRY_AFTER)
                .filter(value -> SECONDS.matcher(value).matches());

        if (retryAfter.isPresent()) {
            return "%s Tente novamente em %s segundos.".formatted(refused, retryAfter.get());
        }
        return "%s Aguarde alguns minutos e tente novamente.".formatted(refused);
    }
}
