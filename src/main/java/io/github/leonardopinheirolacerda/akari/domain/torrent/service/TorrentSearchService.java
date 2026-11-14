package io.github.leonardopinheirolacerda.akari.domain.torrent.service;

import io.github.leonardopinheirolacerda.akari.api.dto.TorrentEdition;
import io.github.leonardopinheirolacerda.akari.api.dto.TorrentProviderType;
import io.github.leonardopinheirolacerda.akari.api.dto.TorrentReleaseResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.TorrentSearchResult;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.extractor.EpisodeNumberExtractor;
import io.github.leonardopinheirolacerda.akari.domain.torrent.extractor.ReleaseTitleExtractor;
import io.github.leonardopinheirolacerda.akari.domain.torrent.mapper.TorrentMapper;
import io.github.leonardopinheirolacerda.akari.domain.torrent.model.ParsedRelease;
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
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

/**
 * Busca releases nos provedores de torrent e as agrupa por edição.
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

        final Map<String, List<ParsedRelease>> releasesByEdition = scrapedSearch
                .releases()
                .stream()
                .map(this::parse)
                .collect(Collectors.groupingBy(ParsedRelease::editionKey, LinkedHashMap::new, Collectors.toList()));

        final List<TorrentEdition> editions = releasesByEdition
                .values()
                .stream()
                .map(this::toEdition)
                .sorted(EDITION_ORDER)
                .toList();

        return new TorrentSearchResult()
                .editions(editions)
                .truncated(scrapedSearch.truncated());
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

    private TorrentEdition toEdition(List<ParsedRelease> editionReleases) {
        final ParsedRelease first = editionReleases.getFirst();

        final List<TorrentReleaseResponse> releases = bestPerEpisode(editionReleases)
                .stream()
                .map(torrentMapper::toResponse)
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
