package io.github.leonardopinheirolacerda.akari.domain.torrent.service;

import io.github.leonardopinheirolacerda.akari.api.dto.TorrentDownloadStatus;
import io.github.leonardopinheirolacerda.akari.api.dto.TorrentEdition;
import io.github.leonardopinheirolacerda.akari.api.dto.TorrentProviderType;
import io.github.leonardopinheirolacerda.akari.api.dto.TorrentReleaseResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.TorrentSearchResult;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.extractor.EpisodeNumberExtractor;
import io.github.leonardopinheirolacerda.akari.domain.torrent.extractor.ReleaseTitleExtractor;
import io.github.leonardopinheirolacerda.akari.domain.torrent.mapper.TorrentMapper;
import io.github.leonardopinheirolacerda.akari.domain.torrent.model.ParsedRelease;
import io.github.leonardopinheirolacerda.akari.domain.torrent.model.TorrentRelease;
import io.github.leonardopinheirolacerda.akari.domain.torrent.provider.TorrentProviderClientFactory;
import io.github.leonardopinheirolacerda.akari.domain.torrent.provider.model.ScrapedRelease;
import io.github.leonardopinheirolacerda.akari.domain.torrent.provider.model.ScrapedSearch;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.exceptions.RateLimitedException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

/**
 * Busca releases nos provedores de torrent e as agrupa por edição, com o estado de download de
 * cada release.
 *
 * <p>Também serve o rastreio ({@code TorrentTrackingService}), que acha a edição rastreada na
 * busca e pega os episódios mais recentes dela.
 */
@ApplicationScoped
public class TorrentSearchService {

    // Entre versões do mesmo episódio, vence a maior versão e, empatando, a de mais seeders.
    private static final Comparator<ParsedRelease> RELEASE_PREFERENCE = Comparator
            .comparing(ParsedRelease::version)
            .thenComparing(release -> Objects.requireNonNullElse(release.scrapedRelease().seeders(), 0));

    private static final Comparator<TorrentEdition> EDITION_ORDER = Comparator
            .comparing((TorrentEdition edition) -> Objects.requireNonNullElse(edition.getGroup(), ""), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(TorrentEdition::getLabel, String.CASE_INSENSITIVE_ORDER);

    @Inject
    TorrentProviderClientFactory torrentProviderClientFactory;

    @Inject
    TorrentMapper torrentMapper;

    /**
     * Busca releases num provedor e as agrupa por edição, com uma release por episódio.
     *
     * @param provider provedor onde buscar
     * @param query texto de busca
     * @return as edições encontradas, ordenadas pelo grupo e depois pelo rótulo (nenhuma se nada
     *         bater), e se o provedor cortou a busca no limite de resultados dele
     * @throws RateLimitedException se o provedor recusar por excesso de requisições
     * @throws IntegrationException se o provedor (ou o proxy da VPN) não responder
     */
    public TorrentSearchResult search(TorrentProviderType provider, String query) {
        final ScrapedSearch scrapedSearch = torrentProviderClientFactory
                .getClient(provider)
                .search(query);

        return new TorrentSearchResult()
                .editions(toEditions(scrapedSearch.releases()))
                .truncated(scrapedSearch.truncated());
    }

    /**
     * Busca releases num provedor (todas as páginas) e devolve só uma edição, com uma release
     * por episódio e o estado de download de cada uma.
     *
     * @param provider provedor onde buscar
     * @param query texto de busca
     * @param editionKey chave da edição desejada
     * @return a edição, se ela aparece na busca
     * @throws RateLimitedException se o provedor recusar por excesso de requisições
     * @throws IntegrationException se o provedor (ou o proxy da VPN) não responder
     */
    public Optional<TorrentEdition> findEdition(TorrentProviderType provider, String query, String editionKey) {
        final List<ParsedRelease> releases = findAllReleases(provider, query, editionKey);

        if (releases.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toEdition(releases, downloadStatusesOf(releases)));
    }

    /**
     * Busca todas as releases de uma edição (todas as páginas do provedor), uma por episódio.
     *
     * @param provider provedor onde buscar
     * @param query texto de busca
     * @param editionKey chave da edição desejada
     * @return as releases numeradas da edição, em ordem de episódio, seguidas das sem número
     * @throws RateLimitedException se o provedor recusar por excesso de requisições
     * @throws IntegrationException se o provedor (ou o proxy da VPN) não responder
     */
    public List<ParsedRelease> findAllReleases(TorrentProviderType provider, String query, String editionKey) {
        final ScrapedSearch scrapedSearch = torrentProviderClientFactory
                .getClient(provider)
                .search(query);

        return editionReleasesOf(scrapedSearch.releases(), editionKey);
    }

    /**
     * Busca as releases mais recentes de uma edição: só a primeira página do provedor, uma
     * release por episódio.
     *
     * @param provider provedor onde buscar
     * @param query texto de busca
     * @param editionKey chave da edição desejada
     * @return as releases numeradas da edição, em ordem de episódio, seguidas das sem número
     * @throws RateLimitedException se o provedor recusar por excesso de requisições
     * @throws IntegrationException se o provedor (ou o proxy da VPN) não responder
     */
    public List<ParsedRelease> findLatestReleases(TorrentProviderType provider, String query, String editionKey) {
        final List<ScrapedRelease> scrapedReleases = torrentProviderClientFactory
                .getClient(provider)
                .searchLatest(query);

        return editionReleasesOf(scrapedReleases, editionKey);
    }

    private List<ParsedRelease> editionReleasesOf(List<ScrapedRelease> scrapedReleases, String editionKey) {
        final List<ParsedRelease> editionReleases = scrapedReleases
                .stream()
                .map(this::parse)
                .filter(release -> release.editionKey().equals(editionKey))
                .toList();

        return bestPerEpisode(editionReleases);
    }

    private List<TorrentEdition> toEditions(List<ScrapedRelease> scrapedReleases) {
        final List<ParsedRelease> releases = scrapedReleases
                .stream()
                .map(this::parse)
                .toList();

        final Map<String, TorrentDownloadStatus> downloadStatuses = downloadStatusesOf(releases);

        final Map<String, List<ParsedRelease>> releasesByEdition = releases
                .stream()
                .collect(Collectors.groupingBy(ParsedRelease::editionKey, LinkedHashMap::new, Collectors.toList()));

        return releasesByEdition
                .values()
                .stream()
                .map(editionReleases -> toEdition(bestPerEpisode(editionReleases), downloadStatuses))
                .sorted(EDITION_ORDER)
                .toList();
    }

    private Map<String, TorrentDownloadStatus> downloadStatusesOf(List<ParsedRelease> releases) {
        final List<String> infoHashes = releases
                .stream()
                .map(release -> release.scrapedRelease().infoHash())
                .toList();

        return TorrentRelease.findStatusesByInfoHash(infoHashes);
    }

    private ParsedRelease parse(ScrapedRelease scrapedRelease) {
        final String title = scrapedRelease.title();
        final OptionalInt episode = EpisodeNumberExtractor.extract(title);
        final String group = ReleaseTitleExtractor
                .extractGroup(title)
                .orElse(null);

        return new ParsedRelease(
                scrapedRelease,
                episode.isPresent() ? episode.getAsInt() : null,
                ReleaseTitleExtractor.extractVersion(title),
                group,
                ReleaseTitleExtractor
                        .extractResolution(title)
                        .orElse(null),
                ReleaseTitleExtractor.extractEditionKey(title, group),
                ReleaseTitleExtractor.extractEditionLabel(title)
        );
    }

    private TorrentEdition toEdition(List<ParsedRelease> bestReleases, Map<String, TorrentDownloadStatus> downloadStatuses) {
        final ParsedRelease first = bestReleases.getFirst();

        final List<TorrentReleaseResponse> releases = bestReleases
                .stream()
                .map(release -> torrentMapper
                        .toResponse(release)
                        .downloadStatus(downloadStatuses.get(release.scrapedRelease().infoHash())))
                .toList();

        return new TorrentEdition()
                .key(first.editionKey())
                .group(first.group())
                .label(first.editionLabel())
                .releases(releases);
    }

    private List<ParsedRelease> bestPerEpisode(List<ParsedRelease> editionReleases) {
        final Map<Integer, ParsedRelease> bestByEpisode = new TreeMap<>();
        final List<ParsedRelease> withoutEpisode = new ArrayList<>();

        for (ParsedRelease release : editionReleases) {
            if (release.episode() == null) {
                withoutEpisode.add(release);
                continue;
            }

            bestByEpisode.merge(release.episode(), release, BinaryOperator.maxBy(RELEASE_PREFERENCE));
        }

        final List<ParsedRelease> releases = new ArrayList<>(bestByEpisode.values());
        releases.addAll(withoutEpisode);

        return releases;
    }
}
