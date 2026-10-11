package io.github.leonardopinheirolacerda.akari.domain.ingestion.service;

import io.github.leonardopinheirolacerda.akari.api.dto.AvailableFolderNode;
import io.github.leonardopinheirolacerda.akari.api.dto.IngestSubtreeRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.IngestionStartedResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.MediaType;
import io.github.leonardopinheirolacerda.akari.api.dto.NotificationLevel;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.FolderReconciliationResult;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.registry.IngestionRunRegistry;
import io.github.leonardopinheirolacerda.akari.domain.mediafolder.service.MediaFolderService;
import io.github.leonardopinheirolacerda.akari.domain.notification.service.NotificationService;
import io.github.leonardopinheirolacerda.akari.domain.setting.service.SettingService;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.model.StorageProvider;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.StorageProviderClientFactory;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.StorageProviderClient;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.model.StorageItem;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.service.StorageProviderService;
import io.github.leonardopinheirolacerda.akari.exceptions.BusinessRuleException;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.utils.DirectoryUtils;
import io.github.leonardopinheirolacerda.akari.utils.FileNameUtils;
import io.github.leonardopinheirolacerda.akari.utils.ThreadUtils;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reconciliação subtree ↔ catalog por storage provider — as 3 operações do contrato (ver tag
 * {@code Ingestion} no openapi.yaml), que já documenta o fluxo completo na descrição do
 * {@code POST}.
 *
 * <p>Os Steps 2-3 (sincronizar `MediaFolder`/`VideoFile` com o provider) ficam no
 * {@link ReconciliationService}, não aqui — a thread virtual do Step 1-3 chama cada etapa como
 * uma transação própria, e um bean separado deixa isso explícito na própria estrutura do código
 * (chamada cross-bean, passa pelo proxy do CDI naturalmente), em vez de precisar de um contorno
 * de auto-injeção pra contornar o self-invocation do CDI.
 */
@ApplicationScoped
public class IngestionService {

    // Throttle proativo entre pastas — complementa o retry reativo do RetryUtils (que só age
    // depois de um 429), espaçando as chamadas pra AniList/TMDB antes de levar rate limit.
    private static final int AUTO_BIND_THROTTLE_MILLIS = 1500;

    @Inject
    StorageProviderService storageProviderService;

    @Inject
    StorageProviderClientFactory storageProviderClientFactory;

    @Inject
    ReconciliationService reconciliationService;

    @Inject
    FolderRemovalService folderRemovalService;

    @Inject
    FolderAutoBindService folderAutoBindService;

    @Inject
    NotificationService notificationService;

    @Inject
    SettingService settingService;

    @Inject
    IngestionRunRegistry ingestionRunRegistry;

    @Inject
    MediaFolderService mediaFolderService;

    public IngestionStartedResponse ingestProviderSubtree(Integer providerId, IngestSubtreeRequest ingestSubtreeRequest) {
        final StorageProvider storageProvider = storageProviderService.findOrThrow(providerId);

        if (!settingService.isTmdbConfigured()) {
            Log.warnf("Ingestão do provider %d rejeitada: TMDB não configurado", providerId);
            throw new BusinessRuleException(
                    "TMDB não configurado — cadastre o token na preferência 'tmdb.api-key' antes de ingerir");
        }

        final String rootPath = Objects.requireNonNullElse(ingestSubtreeRequest.getRootPath(), "");

        if (!ingestionRunRegistry.tryStart(providerId, rootPath)) {
            Log.infof("Ingestão do provider %d (rootPath=%s) já em andamento, ignorando", providerId, rootPath);
            return new IngestionStartedResponse().status(IngestionStartedResponse.StatusEnum.STARTED);
        }

        final StorageProviderClient client = storageProviderClientFactory.getClient(storageProvider);
        final MediaType mediaType = ingestSubtreeRequest.getMediaType();

        Thread.ofVirtual().start(() -> runReconciliation(providerId, rootPath, client, mediaType));

        return new IngestionStartedResponse().status(IngestionStartedResponse.StatusEnum.STARTED);
    }

    @Transactional
    public void removeIngestedSubtree(Integer providerId, String rootPath) {
        final String normalizedRootPath = Objects.requireNonNullElse(rootPath, "");

        Log.infof("Removendo subtree ingerido do provider %d (rootPath=%s)", providerId, normalizedRootPath);

        folderRemovalService.removeSubtree(providerId, normalizedRootPath);
    }

    public AvailableFolderNode listAvailableFolders(Integer providerId, String rootPath) {
        final StorageProvider storageProvider = storageProviderService.findOrThrow(providerId);
        final String normalizedRootPath = Objects.requireNonNullElse(rootPath, "");
        final StorageProviderClient client = storageProviderClientFactory.getClient(storageProvider);

        if (!normalizedRootPath.isEmpty() && !storageProviderService.existsPath(client, normalizedRootPath)) {
            throw new BusinessRuleException("O rootPath informado não existe nesse provider");
        }

        final List<StorageItem> remoteDirs;
        try {
            remoteDirs = client.listTree(normalizedRootPath)
                    .stream()
                    .filter(StorageItem::isDir)
                    .toList();

        } catch (RuntimeException e) {
            Log.errorf(e, "Falha ao listar o provider %d (rootPath=%s)", providerId, normalizedRootPath);
            throw new IntegrationException("Falha ao listar o storage provider", e);
        }

        final Set<String> ingestedPaths = mediaFolderService.findSubtree(providerId, normalizedRootPath)
                .stream()
                .map(folder -> folder.path)
                .collect(Collectors.toSet());

        final Map<String, List<StorageItem>> childrenByParentPath = remoteDirs.stream()
                .collect(Collectors.groupingBy(item -> DirectoryUtils.parentOf(item.path())));

        final String rootPathName = rootPath.isEmpty()
                ? ""
                : DirectoryUtils.lastSegmentOf(rootPath);

        return buildNode(
                rootPath,
                rootPathName,
                childrenByParentPath,
                ingestedPaths
        );
    }

    // ---------- Step 1-4: reconciliação em background ----------

    private void runReconciliation(Integer providerId,
                                   String rootPath,
                                   StorageProviderClient client,
                                   MediaType mediaType) {

        try {
            final List<StorageItem> remoteItems;
            try {
                remoteItems = client.listTree(rootPath);
            } catch (RuntimeException e) {
                Log.errorf(e, "Falha ao listar o provider %d (rootPath=%s)", providerId, rootPath);
                notificationService.notify(
                        NotificationLevel.ERROR, null, "Falha ao listar o storage provider na ingestão", null, null);
                return;
            }

            final List<StorageItem> dirs = remoteItems.stream()
                    .filter(StorageItem::isDir)
                    .toList();

            final List<StorageItem> remoteDirs = StorageItem.withSyntheticRoot(rootPath, dirs);

            final List<StorageItem> remoteVideos = remoteItems.stream()
                    .filter(item -> !item.isDir() && FileNameUtils.isVideoFile(item.name()))
                    .toList();

            reconciliationService.reconcileRemovals(providerId, rootPath, remoteDirs, remoteVideos);

            final List<FolderReconciliationResult> foldersWithVideos = new ArrayList<>();
            int foldersCreated = 0;

            for (StorageItem dir : StorageItem.sortedByDepth(remoteDirs)) {
                final List<StorageItem> directVideos = StorageItem.directChildrenOf(remoteVideos, dir.path());
                final FolderReconciliationResult result = reconciliationService.reconcileFolder(
                        providerId,
                        dir,
                        mediaType,
                        directVideos
                );

                if (result.created()) {
                    foldersCreated++;
                }

                if (result.hasDirectVideos()) {
                    foldersWithVideos.add(result);
                }
            }

            for (FolderReconciliationResult result : foldersWithVideos) {
                folderAutoBindService.autoBindFolder(result.folderId(), result.folderName());

                ThreadUtils.sleepUninterruptibly(AUTO_BIND_THROTTLE_MILLIS);
            }

            notificationService.notify(
                    NotificationLevel.INFO,
                    null,
                    "Ingestão concluída: %d pastas criadas, %d pastas com auto-bind".formatted(
                            foldersCreated, foldersWithVideos.size()),
                    null,
                    null);
        } finally {
            ingestionRunRegistry.finish(providerId, rootPath);
        }
    }

    // ---------- helpers ----------

    private AvailableFolderNode buildNode(String path,
                                          String name,
                                          Map<String, List<StorageItem>> childrenByParentPath,
                                          Set<String> ingestedPaths) {

        final AvailableFolderNode node = new AvailableFolderNode()
                .name(name)
                .path(path)
                .ingested(ingestedPaths.contains(path))
                .children(new ArrayList<>());

        for (StorageItem child : childrenByParentPath.getOrDefault(path, List.of())) {
            final AvailableFolderNode childrenItem = buildNode(
                    child.path(),
                    child.name(),
                    childrenByParentPath,
                    ingestedPaths
            );

            node.addChildrenItem(childrenItem);
        }

        return node;
    }

}