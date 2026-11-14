package io.github.leonardopinheirolacerda.akari.domain.torrent.provider;

import io.github.leonardopinheirolacerda.akari.api.dto.TorrentProviderType;
import io.github.leonardopinheirolacerda.akari.domain.torrent.provider.client.TorrentProviderClient;
import io.github.leonardopinheirolacerda.akari.domain.torrent.provider.client.impl.NyaaTorrentProviderClient;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Resolve, a partir do {@link TorrentProviderType}, a implementação de
 * {@link TorrentProviderClient} que lê aquele provedor.
 *
 * <p>Diferente do {@code StorageProviderClientFactory}, cada client é criado uma vez só e
 * reaproveitado: a busca é {@code synchronized} na instância, o que limita os requests
 * simultâneos a um provedor mesmo com buscas concorrentes. Todos compartilham um
 * {@link HttpClient} que sai pelo proxy HTTP do gluetun ({@code akari.torrent.proxy.*}), sem
 * fallback para a conexão direta.
 */
@ApplicationScoped
public class TorrentProviderClientFactory {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    @ConfigProperty(name = "akari.torrent.proxy.host")
    String proxyHost;

    @ConfigProperty(name = "akari.torrent.proxy.port")
    Integer proxyPort;

    NyaaTorrentProviderClient nyaaTorrentProviderClient;

    /**
     * Cria o {@link HttpClient} pelo proxy e um client por provedor.
     */
    @PostConstruct
    void createClients() {
        final HttpClient httpClient = HttpClient
                .newBuilder()
                .proxy(ProxySelector.of(new InetSocketAddress(proxyHost, proxyPort)))
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        nyaaTorrentProviderClient = new NyaaTorrentProviderClient(httpClient);
    }

    /**
     * Resolve o client do provedor informado.
     *
     * @param type provedor de torrent
     * @return a instância única do client daquele provedor
     */
    public TorrentProviderClient getClient(TorrentProviderType type) {
        return switch (type) {
            case NYAA -> nyaaTorrentProviderClient;
        };
    }
}
