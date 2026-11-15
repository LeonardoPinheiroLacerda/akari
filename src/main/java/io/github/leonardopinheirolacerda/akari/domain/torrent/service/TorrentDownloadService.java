package io.github.leonardopinheirolacerda.akari.domain.torrent.service;

import io.github.leonardopinheirolacerda.akari.api.dto.AnimeBindingRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.IngestSubtreeRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.MediaType;
import io.github.leonardopinheirolacerda.akari.api.dto.NotificationLevel;
import io.github.leonardopinheirolacerda.akari.api.dto.StorageProviderType;
import io.github.leonardopinheirolacerda.akari.api.dto.TorrentDownloadPage;
import io.github.leonardopinheirolacerda.akari.api.dto.TorrentDownloadRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.TorrentDownloadResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.TorrentDownloadStatus;
import io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos.AnilistMediaResponse;
import io.github.leonardopinheirolacerda.akari.domain.anilist.service.AnilistSearchService;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeOverrideService;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeService;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.extractor.EpisodeNumberExtractor;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.service.IngestionService;
import io.github.leonardopinheirolacerda.akari.domain.mediafolder.model.MediaFolder;
import io.github.leonardopinheirolacerda.akari.domain.mediafolder.service.MediaFolderService;
import io.github.leonardopinheirolacerda.akari.domain.notification.service.NotificationService;
import io.github.leonardopinheirolacerda.akari.domain.setting.service.SettingService;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.model.StorageProvider;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.StorageProviderClientFactory;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.service.StorageProviderService;
import io.github.leonardopinheirolacerda.akari.domain.torrent.client.QbittorrentClient;
import io.github.leonardopinheirolacerda.akari.domain.torrent.client.dtos.QbittorrentAddResponse;
import io.github.leonardopinheirolacerda.akari.domain.torrent.client.dtos.QbittorrentTorrentResponse;
import io.github.leonardopinheirolacerda.akari.domain.torrent.mapper.TorrentMapper;
import io.github.leonardopinheirolacerda.akari.domain.torrent.model.TorrentRelease;
import io.github.leonardopinheirolacerda.akari.exceptions.BusinessRuleException;
import io.github.leonardopinheirolacerda.akari.exceptions.ConflictException;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationNotConfiguredException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.github.leonardopinheirolacerda.akari.model.PageResult;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Baixa releases de torrent pelo qBittorrent e, quando cada download termina, move os arquivos
 * para a pasta do anime na biblioteca, remove o torrent do qBittorrent e ingere a pasta.
 *
 * <p>O qBittorrent baixa sempre na pasta de trabalho {@code akari.torrent.download-dir} (a
 * {@code TORRENT_DOWNLOAD_DIR} montada no container no mesmo caminho do host), uma subpasta por
 * info-hash. A biblioteca (preferência {@code torrent.library-provider-id}) é só o destino da
 * movimentação, então trocar de provider não exige mexer no qBittorrent.
 *
 * <p>A sessão da Web API do qBittorrent (o cookie do login) é guardada e reaproveitada entre as
 * chamadas, que são serializadas; um {@code 403} (sessão expirada) refaz o login uma vez.
 */
@ApplicationScoped
public class TorrentDownloadService {

    private static final String QBITTORRENT_TAG = "akari";

    private static final String NO_SUBFOLDER = "NoSubfolder";

    private static final double COMPLETE = 1.0;

    private static final int QBITTORRENT_ATTEMPTS = 2;

    // Caracteres proibidos em nome de pasta.
    private static final Pattern FORBIDDEN_FOLDER_CHARACTERS = Pattern.compile("[/\\\\:*?\"<>|]");

    private static final Pattern WHITESPACES = Pattern.compile("\\s+");

    private static final Pattern INFO_HASH = Pattern.compile("(?i)btih:([0-9a-f]{40})");

    @ConfigProperty(name = "akari.torrent.qbittorrent.username")
    String qbittorrentUsername;

    @ConfigProperty(name = "akari.torrent.qbittorrent.password")
    String qbittorrentPassword;

    @ConfigProperty(name = "akari.torrent.download-dir")
    String downloadDir;

    @Inject
    @RestClient
    QbittorrentClient qbittorrentClient;

    @Inject
    SettingService settingService;

    @Inject
    StorageProviderService storageProviderService;

    @Inject
    StorageProviderClientFactory storageProviderClientFactory;

    @Inject
    MediaFolderService mediaFolderService;

    @Inject
    AnimeService animeService;

    @Inject
    AnimeOverrideService animeOverrideService;

    @Inject
    AnilistSearchService anilistSearchService;

    @Inject
    IngestionService ingestionService;

    @Inject
    NotificationService notificationService;

    @Inject
    TorrentMapper torrentMapper;

    private String qbittorrentCookie;

    /**
     * Envia uma release ao qBittorrent, salvando na pasta do anime na biblioteca.
     *
     * <p>O info-hash, que identifica a release, sai do próprio link magnet.
     *
     * <p>Fluxo: release que já está baixando ou já foi baixada → devolvida como está; senão
     * resolve o provider da biblioteca e a pasta do anime (a já vinculada a ele, ou uma nova
     * {@code <título romaji>} vinculada na hora), envia o magnet ao qBittorrent para baixar em
     * {@code <pasta de trabalho>/<info-hash>} (se o torrent ainda não estiver lá) e grava a
     * release como {@code DOWNLOADING}. Qualquer falha desfaz a pasta e o vínculo criados.
     *
     * @param torrentDownloadRequest release a baixar e o anime a que ela pertence
     * @return o download
     * @throws IntegrationNotConfiguredException se a preferência {@code torrent.library-provider-id}
     *         estiver vazia
     * @throws BusinessRuleException se o magnet não trouxer o info-hash, se o provider da
     *         biblioteca não for {@code LOCAL}, ou se a pasta nova já estiver vinculada a outro anime
     * @throws ConflictException se o anime estiver vinculado a uma pasta de outro provider
     * @throws ResourceNotFoundException se o provider da biblioteca ou o anime na AniList não
     *         existirem
     * @throws IntegrationException se o qBittorrent ou a AniList falharem
     */
    @Transactional
    public TorrentDownloadResponse startDownload(TorrentDownloadRequest torrentDownloadRequest) {
        final String infoHash = infoHashOf(torrentDownloadRequest.getMagnetUri());
        final Optional<TorrentRelease> existing = TorrentRelease.findByInfoHash(infoHash);

        if (existing.isPresent() && existing.get().status != TorrentDownloadStatus.NEW) {
            Log.infof("Release %s já está em %s", infoHash, existing.get().status);

            return toDownloadResponse(existing.get());
        }

        prepareAnimeFolder(torrentDownloadRequest.getAnilistId(), findLibraryProvider());

        final String savePath = Path
                .of(downloadDir, infoHash)
                .toString();

        final boolean added = callQbittorrent(cookie -> addIfAbsent(
                cookie,
                infoHash,
                torrentDownloadRequest.getMagnetUri(),
                savePath
        ));

        final OptionalInt episode = EpisodeNumberExtractor.extract(torrentDownloadRequest.getTitle());

        final TorrentRelease release = existing.orElseGet(TorrentRelease::new);
        release.provider = torrentDownloadRequest.getProvider();
        release.infoHash = infoHash;
        release.magnetUri = torrentDownloadRequest.getMagnetUri();
        release.title = torrentDownloadRequest.getTitle();
        release.episode = episode.isPresent() ? episode.getAsInt() : null;
        release.anime = animeService.findOrThrow(torrentDownloadRequest.getAnilistId());
        release.status = TorrentDownloadStatus.DOWNLOADING;
        release.progress = 0.0;
        release.persist();

        Log.infof("Download %d (%s) em %s, destino pasta %d (enviado agora ao qBittorrent: %s)", release.id, release.title, savePath, release.anime.folder.id, added);

        return toDownloadResponse(release);
    }

    /**
     * Confere, sem criar nada, se um anime pode receber downloads: a biblioteca está configurada
     * e é {@code LOCAL}, e o anime, se já tem pasta, tem a pasta na biblioteca.
     *
     * <p>Só lê, mas roda numa transação para o anime continuar na sessão: chamado de fora de uma
     * transação (o rastreio, que busca no provedor sem segurar conexão), o anime voltaria
     * desanexado e a leitura do provider da pasta, que é lazy, falharia.
     *
     * @param anilistId id do anime na AniList
     * @throws IntegrationNotConfiguredException se a preferência {@code torrent.library-provider-id}
     *         estiver vazia
     * @throws BusinessRuleException se o provider da biblioteca não for {@code LOCAL}
     * @throws ConflictException se o anime estiver vinculado a uma pasta de outro provider
     * @throws ResourceNotFoundException se o provider da biblioteca não existir
     */
    @Transactional
    public void checkDownloadable(Integer anilistId) {
        final StorageProvider library = findLibraryProvider();
        final Optional<Anime> anime = animeService.find(anilistId);

        if (anime.isPresent()) {
            checkInLibrary(anime.get().folder, library);
        }
    }

    /**
     * Pagina os downloads em andamento e concluídos, do mais recente para o mais antigo.
     *
     * @param page página desejada, 0-based
     * @param size tamanho da página
     * @return a página de downloads; vazia se nada foi baixado
     */
    public TorrentDownloadPage listDownloads(Integer page, Integer size) {
        final PageResult<TorrentRelease> pageResult = TorrentRelease.findDownloadsPage(page, size);

        final List<TorrentDownloadResponse> downloads = pageResult
                .data()
                .stream()
                .map(this::toDownloadResponse)
                .toList();

        return new TorrentDownloadPage()
                .data(downloads)
                .page(pageResult.page())
                .size(pageResult.size())
                .totalElements(pageResult.totalElements())
                .totalPages(pageResult.totalPages());
    }

    /**
     * Sincroniza os downloads em andamento com o qBittorrent.
     *
     * <p>Para cada release {@code DOWNLOADING}: fora do qBittorrent → volta para {@code NEW};
     * ainda baixando → atualiza o progresso; terminado → move os arquivos para a pasta do anime,
     * remove o torrent do qBittorrent, ingere a pasta, marca {@code READY} e notifica. Os
     * arquivos só saem com o torrent inteiro pronto: as peças de um torrent cruzam a fronteira
     * entre arquivos, e mover um arquivo antes faria o qBittorrent baixar de novo a peça que ele
     * divide com o seguinte.
     *
     * <p>Roda fora de transação: a ingestão cria as tarefas em background, que precisam enxergar
     * a ingestão já gravada, então cada release grava o seu andamento numa transação curta
     * própria. Falha numa release (ex.: erro ao mover) não impede as demais e é tentada de novo
     * no ciclo seguinte.
     *
     * @throws IntegrationException se o qBittorrent não responder
     */
    public void syncDownloads() {
        final List<TorrentRelease> releases = TorrentRelease.listDownloading();

        if (releases.isEmpty()) {
            return;
        }

        final String hashes = releases
                .stream()
                .map(release -> release.infoHash)
                .collect(Collectors.joining("|"));

        final Map<String, QbittorrentTorrentResponse> torrentsByHash = callQbittorrent(cookie -> qbittorrentClient.listTorrents(cookie, hashes))
                .stream()
                .collect(Collectors.toMap(QbittorrentTorrentResponse::hash, Function.identity()));

        for (TorrentRelease release : releases) {
            try {
                syncRelease(release, torrentsByHash.get(release.infoHash));
            } catch (RuntimeException e) {
                Log.warnf(e, "Falha ao sincronizar o download %d (%s)", release.id, release.title);
            }
        }
    }

    private TorrentDownloadResponse toDownloadResponse(TorrentRelease release) {
        return torrentMapper
                .toDownloadResponse(release)
                .anime(animeOverrideService.toBindingSummary(release.anime));
    }

    private String infoHashOf(String magnetUri) {
        final Matcher infoHash = INFO_HASH.matcher(magnetUri);

        if (!infoHash.find()) {
            throw new BusinessRuleException("O link magnet não traz o info-hash da release (xt=urn:btih:<40 caracteres hexadecimais>)");
        }
        return infoHash
                .group(1)
                .toLowerCase(Locale.ROOT);
    }

    private StorageProvider findLibraryProvider() {
        final Integer providerId = settingService.getTorrentLibraryProviderId();

        if (providerId == null) {
            throw new IntegrationNotConfiguredException(
                    "Biblioteca de torrents não configurada. Cadastre o id do storage provider LOCAL da biblioteca na preferência 'torrent.library-provider-id'.");
        }

        final StorageProvider library = storageProviderService.findOrThrow(providerId);

        if (library.type != StorageProviderType.LOCAL) {
            throw new BusinessRuleException(
                    "A biblioteca de torrents precisa ser um storage provider LOCAL, mas o provider %d da preferência 'torrent.library-provider-id' é %s"
                            .formatted(library.id, library.type));
        }

        return library;
    }

    private void prepareAnimeFolder(Integer anilistId, StorageProvider library) {
        final Optional<Anime> anime = animeService.find(anilistId);

        if (anime.isPresent()) {
            checkInLibrary(anime.get().folder, library);
            return;
        }

        final String folderName = folderNameOf(anilistSearchService.findById(anilistId, false));
        final MediaFolder folder = mediaFolderService.findOrCreate(library.id, folderName, folderName, MediaType.ANIME);

        animeService.bindAnime(anilistId, new AnimeBindingRequest().folderId(folder.id));

        Log.infof("Pasta %d (%s) criada na biblioteca e vinculada ao anime %d", folder.id, folderName, anilistId);
    }

    private void checkInLibrary(MediaFolder folder, StorageProvider library) {
        if (!folder.storageProvider.id.equals(library.id)) {
            throw new ConflictException(
                    "Esse anime já está vinculado à pasta \"%s\" de outro storage provider (%s). Desvincule a pasta antes de baixar por torrent."
                            .formatted(folder.path, folder.storageProvider.type));
        }
    }

    private String folderNameOf(AnilistMediaResponse anilistMedia) {
        final String title = Objects.requireNonNullElse(
                anilistMedia.title().romaji(),
                anilistMedia.title().english()
        );

        final String allowedTitle = FORBIDDEN_FOLDER_CHARACTERS
                .matcher(title)
                .replaceAll(" ");

        return WHITESPACES
                .matcher(allowedTitle)
                .replaceAll(" ")
                .strip();
    }

    private boolean addIfAbsent(String cookie, String infoHash, String magnetUri, String savePath) {
        if (!qbittorrentClient.listTorrents(cookie, infoHash).isEmpty()) {
            Log.infof("Torrent %s já estava no qBittorrent", infoHash);

            return false;
        }

        final QbittorrentAddResponse result = qbittorrentClient.addTorrent(
                cookie,
                magnetUri,
                savePath,
                QBITTORRENT_TAG,
                NO_SUBFOLDER
        );

        if (result.failureCount() > 0) {
            throw new IntegrationException("O qBittorrent recusou o torrent %s".formatted(infoHash));
        }

        return true;
    }

    private void syncRelease(TorrentRelease release, QbittorrentTorrentResponse torrent) {
        if (torrent == null) {
            Log.warnf("Download %d (%s) não está mais no qBittorrent, volta para NEW", release.id, release.title);

            QuarkusTransaction
                    .requiringNew()
                    .run(() -> markRemoved(release.id));
            return;
        }

        if (torrent.progress() < COMPLETE) {
            QuarkusTransaction
                    .requiringNew()
                    .run(() -> updateProgress(release.id, torrent.progress()));
            return;
        }

        finishDownload(release);
    }

    private void finishDownload(TorrentRelease release) {
        final MediaFolder folder = release.anime.folder;
        final Path workDir = Path.of(downloadDir, release.infoHash);

        storageProviderClientFactory
                .getClient(folder.storageProvider)
                .importFiles(workDir, folder.path);

        callQbittorrent(cookie -> qbittorrentClient.deleteTorrents(cookie, release.infoHash, true));

        deleteWorkDir(workDir);

        Log.infof("Download %d (%s) movido para a pasta %d e removido do qBittorrent", release.id, release.title, folder.id);

        ingestFolder(release);

        QuarkusTransaction
                .requiringNew()
                .run(() -> markReady(release.id));

        notifyCompleted(release);
    }

    private void deleteWorkDir(Path workDir) {
        if (!Files.exists(workDir)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(workDir)) {
            final List<Path> deepestFirst = paths
                    .sorted(Comparator.reverseOrder())
                    .toList();

            for (Path path : deepestFirst) {
                Files.delete(path);
            }
        } catch (IOException e) {
            Log.warnf(e, "Não foi possível apagar a pasta de trabalho %s", workDir);
        }
    }

    private void ingestFolder(TorrentRelease release) {
        final MediaFolder folder = release.anime.folder;

        final IngestSubtreeRequest request = new IngestSubtreeRequest()
                .rootPath(folder.path)
                .mediaType(MediaType.ANIME);

        try {
            ingestionService.createIngestionTasks(folder.storageProvider.id, request);
        } catch (RuntimeException e) {
            Log.warnf(e, "Falha ao ingerir a pasta %d do download %d", folder.id, release.id);

            notificationService.notify(
                    NotificationLevel.ERROR,
                    "Falha ao ingerir download",
                    "\"%s\" foi baixado, mas a ingestão da pasta \"%s\" falhou: %s".formatted(release.title, folder.path, e.getMessage()),
                    folder.id,
                    animeOverrideService.resolveThumbnailMedium(release.anime)
            );
        }
    }

    private void markRemoved(Integer releaseId) {
        final TorrentRelease release = TorrentRelease.findById(releaseId);

        release.status = TorrentDownloadStatus.NEW;
        release.progress = 0.0;
    }

    private void updateProgress(Integer releaseId, Double progress) {
        final TorrentRelease release = TorrentRelease.findById(releaseId);

        release.progress = progress;
    }

    private void markReady(Integer releaseId) {
        final TorrentRelease release = TorrentRelease.findById(releaseId);

        release.status = TorrentDownloadStatus.READY;
        release.progress = COMPLETE;
        release.completedAt = OffsetDateTime.now();
    }

    private void notifyCompleted(TorrentRelease release) {
        final Anime anime = release.anime;
        final String animeTitle = animeOverrideService.resolveTitle(anime);

        notificationService.notify(
                NotificationLevel.INFO,
                "Download concluído",
                completedMessage(animeTitle, release),
                anime.folder.id,
                animeOverrideService.resolveThumbnailMedium(anime)
        );
    }

    private String completedMessage(String animeTitle, TorrentRelease release) {
        if (release.episode == null) {
            return "%s: \"%s\" já pode ser assistido".formatted(animeTitle, release.title);
        }
        return "%s: episódio %d já pode ser assistido".formatted(animeTitle, release.episode);
    }

    private synchronized <T> T callQbittorrent(Function<String, T> call) {
        for (int attempt = 1; ; attempt++) {
            try {
                if (qbittorrentCookie == null) {
                    qbittorrentCookie = login();
                }

                return call.apply(qbittorrentCookie);
            } catch (WebApplicationException e) {
                final int status = e.getResponse().getStatus();

                if (status != HttpURLConnection.HTTP_FORBIDDEN || attempt == QBITTORRENT_ATTEMPTS) {
                    throw new IntegrationException(
                            "O qBittorrent respondeu HTTP %d. Confira se a stack está no ar (docker compose up -d) e as credenciais QBITTORRENT_USERNAME/QBITTORRENT_PASSWORD."
                                    .formatted(status),
                            e);
                }

                Log.info("Sessão do qBittorrent expirada, fazendo login de novo");

                qbittorrentCookie = null;
            } catch (ProcessingException e) {
                throw new IntegrationException(
                        "Falha ao acessar o qBittorrent. Confira se a stack está no ar (docker compose up -d).",
                        e);
            }
        }
    }

    private String login() {
        try (Response response = qbittorrentClient.login(qbittorrentUsername, qbittorrentPassword)) {
            final String cookie = response
                    .getCookies()
                    .values()
                    .stream()
                    .map(sessionCookie -> sessionCookie.getName() + "=" + sessionCookie.getValue())
                    .collect(Collectors.joining("; "));

            if (cookie.isEmpty()) {
                throw new IntegrationException(
                        "O qBittorrent recusou o login. Confira QBITTORRENT_USERNAME e QBITTORRENT_PASSWORD no .env.");
            }

            return cookie;
        }
    }
}
