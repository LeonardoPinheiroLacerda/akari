package io.github.leonardopinheirolacerda.akari.domain.torrent.service;

import io.github.leonardopinheirolacerda.akari.api.dto.TorrentDownloadRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.TorrentEdition;
import io.github.leonardopinheirolacerda.akari.api.dto.TorrentReleaseResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.TrackedAnimeRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.TrackedAnimeResponse;
import io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos.AnilistCoverImageResponse;
import io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos.AnilistMediaResponse;
import io.github.leonardopinheirolacerda.akari.domain.anilist.service.AnilistSearchService;
import io.github.leonardopinheirolacerda.akari.domain.torrent.mapper.TorrentMapper;
import io.github.leonardopinheirolacerda.akari.domain.torrent.model.ParsedRelease;
import io.github.leonardopinheirolacerda.akari.domain.torrent.model.TrackedAnime;
import io.github.leonardopinheirolacerda.akari.exceptions.BusinessRuleException;
import io.github.leonardopinheirolacerda.akari.exceptions.ConflictException;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationNotConfiguredException;
import io.github.leonardopinheirolacerda.akari.exceptions.RateLimitedException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Rastreia animes em lançamento: guarda o anime, a query e a edição escolhida e, a cada checagem,
 * baixa os episódios novos da edição.
 *
 * <p><strong>Aceito</strong>: este service chama outros dois services do mesmo domínio — o
 * {@link TorrentSearchService}, pra interpretar e agrupar as releases, e o
 * {@link TorrentDownloadService}, pra baixar —, porque o rastreio é justamente a orquestração dos
 * dois; duplicar essa lógica aqui seria pior que a exceção à regra de um service por resource.
 */
@ApplicationScoped
public class TorrentTrackingService {

    @Inject
    TorrentSearchService torrentSearchService;

    @Inject
    TorrentDownloadService torrentDownloadService;

    @Inject
    AnilistSearchService anilistSearchService;

    @Inject
    TorrentMapper torrentMapper;

    /**
     * Lista os rastreios, do mais recente para o mais antigo.
     *
     * @return todos os rastreios; lista vazia se não há nenhum
     */
    public List<TrackedAnimeResponse> listTracked() {
        return TrackedAnime
                .listNewestFirst()
                .stream()
                .map(torrentMapper::toTrackedResponse)
                .toList();
    }

    /**
     * Começa a rastrear um anime numa edição de um provedor.
     *
     * <p>Fluxo: anime já rastreado → {@code 409}; confere que o anime pode receber downloads
     * (biblioteca configurada, pasta na biblioteca); busca a query no provedor (todas as páginas)
     * e confere que a edição aparece nela; grava o rastreio com o título e a capa da AniList e,
     * como último episódio encontrado, o maior que a edição já tem — só os que saírem depois são
     * baixados sozinhos. A busca e a AniList rodam fora de transação; só a gravação abre uma,
     * curta.
     *
     * @param trackedAnimeRequest anime, provedor, query e edição a rastrear
     * @return o rastreio criado
     * @throws ConflictException se o anime já é rastreado, ou se está vinculado a uma pasta de
     *         outro storage provider
     * @throws BusinessRuleException se a edição não aparecer na busca, ou se a biblioteca não for
     *         {@code LOCAL}
     * @throws ResourceNotFoundException se o anime não existir na AniList
     * @throws IntegrationNotConfiguredException se a biblioteca não estiver configurada
     * @throws RateLimitedException se o provedor recusar por excesso de requisições
     * @throws IntegrationException se o provedor ou a AniList falharem
     */
    public TrackedAnimeResponse track(TrackedAnimeRequest trackedAnimeRequest) {
        final Integer anilistId = trackedAnimeRequest.getAnilistId();

        if (TrackedAnime.findByAnilistId(anilistId).isPresent()) {
            throw new ConflictException("Esse anime já está sendo rastreado");
        }

        torrentDownloadService.checkDownloadable(anilistId);

        final TorrentEdition edition = torrentSearchService
                .findEdition(trackedAnimeRequest.getProvider(), trackedAnimeRequest.getQuery(), trackedAnimeRequest.getEditionKey())
                .orElseThrow(() -> new BusinessRuleException(
                        "A edição informada não aparece na busca \"%s\" do provedor %s"
                                .formatted(trackedAnimeRequest.getQuery(), trackedAnimeRequest.getProvider())));

        final AnilistMediaResponse anilistMedia = anilistSearchService.findById(anilistId, false);
        final AnilistCoverImageResponse coverImage = anilistMedia.coverImage();

        final TrackedAnime trackedAnime = new TrackedAnime();
        trackedAnime.anilistId = anilistId;
        trackedAnime.title = Objects.requireNonNullElse(anilistMedia.title().romaji(), anilistMedia.title().english());
        trackedAnime.coverImage = coverImage == null ? null : coverImage.large();
        trackedAnime.provider = trackedAnimeRequest.getProvider();
        trackedAnime.query = trackedAnimeRequest.getQuery();
        trackedAnime.editionKey = edition.getKey();
        trackedAnime.editionGroup = edition.getGroup();
        trackedAnime.editionLabel = edition.getLabel();
        trackedAnime.latestEpisode = latestEpisodeOf(edition);

        QuarkusTransaction
                .requiringNew()
                .run(trackedAnime::persist);

        Log.infof("Anime %d rastreado na edição %s (último episódio encontrado: %s)", anilistId, edition.getKey(), trackedAnime.latestEpisode);

        return torrentMapper.toTrackedResponse(trackedAnime);
    }

    /**
     * Para de rastrear um anime. Idempotente: rastreio inexistente não faz nada.
     *
     * @param trackedId id do rastreio
     */
    @Transactional
    public void untrack(Integer trackedId) {
        TrackedAnime.deleteById(trackedId);
    }

    /**
     * Refaz a busca do rastreio no provedor e devolve só a edição rastreada.
     *
     * @param trackedId id do rastreio
     * @return a edição rastreada, com o estado de download de cada release; sem releases se ela
     *         sumiu da busca
     * @throws ResourceNotFoundException se o rastreio não existir
     * @throws RateLimitedException se o provedor recusar por excesso de requisições
     * @throws IntegrationException se o provedor (ou o proxy da VPN) não responder
     */
    public TorrentEdition listReleases(Integer trackedId) {
        final TrackedAnime trackedAnime = TrackedAnime
                .<TrackedAnime>findByIdOptional(trackedId)
                .orElseThrow(() -> new ResourceNotFoundException("Não foi possível localizar um rastreio com o id informado"));

        return torrentSearchService
                .findEdition(trackedAnime.provider, trackedAnime.query, trackedAnime.editionKey)
                .orElseGet(() -> new TorrentEdition()
                        .key(trackedAnime.editionKey)
                        .group(trackedAnime.editionGroup)
                        .label(trackedAnime.editionLabel)
                        .releases(List.of()));
    }

    /**
     * Checa todos os rastreios e baixa os episódios novos.
     *
     * <p>Para cada rastreio: lê a primeira página da busca e baixa, em ordem, os episódios da
     * edição acima do último encontrado, depois grava o novo último encontrado. Se a primeira
     * página não alcança o último encontrado (nenhum episódio da edição nela é igual ou anterior
     * a ele), pode haver episódios novos nas páginas seguintes — depois de checagens com falha,
     * ou numa busca movimentada —, então a checagem usa a busca completa.
     *
     * <p>Uma falha (anime numa pasta de outro provider, provedor fora, ...) fica em
     * {@code lastError} e não impede os demais rastreios; o rastreio é checado de novo na próxima
     * vez, e os episódios que já foram enviados ao qBittorrent não são enviados de novo. Um
     * {@code 429} encerra o ciclo: pedir as buscas dos demais rastreios só prolongaria o bloqueio.
     */
    public void checkTrackedAnimes() {
        for (TrackedAnime trackedAnime : TrackedAnime.listNewestFirst()) {
            try {
                checkTrackedAnime(trackedAnime);
            } catch (RateLimitedException e) {
                Log.warnf("Provedor recusou a checagem do rastreio %d por excesso de requisições; os demais ficam para o próximo ciclo", trackedAnime.id);

                markFailed(trackedAnime.id, e);
                return;
            } catch (RuntimeException e) {
                Log.warnf(e, "Falha ao checar o rastreio %d (anime %d)", trackedAnime.id, trackedAnime.anilistId);

                markFailed(trackedAnime.id, e);
            }
        }
    }

    private Integer latestEpisodeOf(TorrentEdition edition) {
        return edition
                .getReleases()
                .stream()
                .map(TorrentReleaseResponse::getEpisode)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private void checkTrackedAnime(TrackedAnime trackedAnime) {
        final List<ParsedRelease> latestReleases = torrentSearchService.findLatestReleases(
                trackedAnime.provider,
                trackedAnime.query,
                trackedAnime.editionKey
        );

        final List<ParsedRelease> newReleases = newReleasesOf(trackedAnime, releasesReachingLatestEpisode(trackedAnime, latestReleases));

        for (ParsedRelease release : newReleases) {
            Log.infof("Episódio %d novo no rastreio %d: %s", release.episode(), trackedAnime.id, release.scrapedRelease().title());

            torrentDownloadService.startDownload(new TorrentDownloadRequest()
                    .provider(trackedAnime.provider)
                    .magnetUri(release.scrapedRelease().magnetUri())
                    .title(release.scrapedRelease().title())
                    .anilistId(trackedAnime.anilistId));
        }

        final Integer latestEpisode = newReleases.isEmpty()
                ? trackedAnime.latestEpisode
                : newReleases.getLast().episode();

        markChecked(trackedAnime.id, latestEpisode);
    }

    private List<ParsedRelease> releasesReachingLatestEpisode(TrackedAnime trackedAnime, List<ParsedRelease> latestReleases) {
        if (trackedAnime.latestEpisode == null) {
            return latestReleases;
        }

        final boolean reachesLatestEpisode = latestReleases
                .stream()
                .anyMatch(release -> release.episode() != null && release.episode() <= trackedAnime.latestEpisode);

        if (reachesLatestEpisode) {
            return latestReleases;
        }

        Log.infof("Primeira página não alcança o episódio %d do rastreio %d; usando a busca completa", trackedAnime.latestEpisode, trackedAnime.id);

        return torrentSearchService.findAllReleases(trackedAnime.provider, trackedAnime.query, trackedAnime.editionKey);
    }

    private List<ParsedRelease> newReleasesOf(TrackedAnime trackedAnime, List<ParsedRelease> releases) {
        return releases
                .stream()
                .filter(release -> release.episode() != null)
                .filter(release -> trackedAnime.latestEpisode == null || release.episode() > trackedAnime.latestEpisode)
                .toList();
    }

    private void markChecked(Integer trackedId, Integer latestEpisode) {
        // Update por id: o rastreio pode ter sido apagado durante a checagem.
        QuarkusTransaction
                .requiringNew()
                .run(() -> TrackedAnime.update(
                        "latestEpisode = ?1, lastCheckedAt = ?2, lastError = null where id = ?3",
                        latestEpisode,
                        OffsetDateTime.now(),
                        trackedId
                ));
    }

    private void markFailed(Integer trackedId, RuntimeException failure) {
        final String error = Objects.requireNonNullElse(
                failure.getMessage(),
                "Falha inesperada na checagem (%s)".formatted(failure.getClass().getSimpleName())
        );

        QuarkusTransaction
                .requiringNew()
                .run(() -> TrackedAnime.update("lastError = ?1 where id = ?2", error, trackedId));
    }
}
