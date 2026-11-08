package io.github.leonardopinheirolacerda.akari.domain.ingestion.service;

import io.github.leonardopinheirolacerda.akari.api.dto.*;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeService;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.Ingestion;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums.IngestionStatus;
import io.github.leonardopinheirolacerda.akari.domain.mediafolder.model.MediaFolder;
import io.github.leonardopinheirolacerda.akari.domain.mediafolder.service.MediaFolderService;
import io.github.leonardopinheirolacerda.akari.domain.setting.service.SettingService;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.model.StorageProvider;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.StorageProviderClientFactory;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.StorageProviderClient;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.model.StorageItem;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.service.StorageProviderService;
import io.github.leonardopinheirolacerda.akari.exceptions.BusinessRuleException;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.github.leonardopinheirolacerda.akari.utils.DirectoryUtils;
import io.quarkus.logging.Log;
import io.quarkus.virtual.threads.VirtualThreads;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * Ingestão de subtrees de storage provider no catalog: dispara a ingestão, lista as pastas
 * remotas marcando as já ingeridas e remove o que foi ingerido sob um subtree.
 *
 * <p>O disparo roda em duas fases. A primeira é sequencial, na thread da requisição: cria as
 * {@code MediaFolder}s do subtree da mais rasa pra mais funda, cada uma na sua transação, pra
 * que o pai já esteja no banco quando o filho resolve o {@code parent} pelo path. A segunda é
 * paralela, numa thread virtual por pasta: o {@link IngestionTaskService} registra os arquivos
 * de vídeo e a {@code IngestionTask} da pasta, sem criar pasta nenhuma — criar pastas em
 * paralelo faria o filho não achar o pai ainda não commitado.
 */
@ApplicationScoped
public class IngestionService {

    @Inject
    StorageProviderService storageProviderService;

    @Inject
    StorageProviderClientFactory storageProviderClientFactory;

    @Inject
    SettingService settingService;

    @Inject
    MediaFolderService mediaFolderService;

    @Inject
    IngestionTaskService ingestionTaskService;

    @Inject
    @VirtualThreads
    ExecutorService executorService;

    @Inject
    AnimeService animeService;

    /**
     * Registra no catalog as pastas e os arquivos de vídeo de um subtree do provider e cria uma
     * {@link Ingestion} com uma {@code IngestionTask} por pasta.
     *
     * <p>Fluxo: valida o TMDB configurado e o provider → lista a árvore remota a partir de
     * {@code rootPath}, acrescentando o próprio {@code rootPath} como pasta (o {@code listTree}
     * só devolve descendentes) → persiste a {@link Ingestion} como {@code PENDING} → pra cada
     * pasta, da mais rasa pra mais funda, cria a {@code MediaFolder} se não existir (transação
     * própria), lista os vídeos diretos dela e submete a criação da task numa thread virtual.
     * Toda pasta ganha task, inclusive as sem vídeos diretos.
     *
     * <p>Devolve depois de submeter as tasks, sem esperar por elas. Falha de uma task fica só
     * no log, sem afetar as demais. Não há deduplicação: dois disparos simultâneos sobre o
     * mesmo subtree rodam juntos. {@code rootPath} não é validado contra o provider.
     *
     * @param providerId id do storage provider a ingerir
     * @param ingestSubtreeRequest subtree a ingerir ({@code rootPath}; {@code null} vira a raiz
     *                             do provider) e tipo de mídia gravado nas pastas novas — pastas
     *                             já existentes mantêm o tipo que têm
     * @return status {@code STARTED}
     * @throws BusinessRuleException se o TMDB não estiver configurado (preferência
     *         {@code tmdb.api-key})
     * @throws ResourceNotFoundException se o {@code providerId} não existir
     */
    public IngestionStartedResponse createIngestionTasks(Integer providerId, IngestSubtreeRequest ingestSubtreeRequest) {

        if (!settingService.isTmdbConfigured()) {
            Log.warnf("Ingestão do provider %d rejeitada: TMDB não configurado", providerId);
            throw new BusinessRuleException(
                    "TMDB não configurado — cadastre o token na preferência 'tmdb.api-key' antes de ingerir");
        }

        final StorageProvider storageProvider = storageProviderService.findOrThrow(providerId);
        final StorageProviderClient client = storageProviderClientFactory.getClient(storageProvider);

        final String rootPath = Objects.requireNonNullElse(ingestSubtreeRequest.getRootPath(), "");

        final List<StorageItem> storageDirs = client.listTree(rootPath)
                .stream()
                .filter(StorageItem::isDir)
                .toList();

        final List<StorageItem> withSyntheticRoot = StorageItem.withSyntheticRoot(rootPath, storageDirs);

        final List<StorageItem> storageItems = StorageItem.sortedByDepth(withSyntheticRoot);

        Ingestion ingestion = new Ingestion();
        ingestion.status = IngestionStatus.PENDING;

        createIngestion(ingestion);

        storageItems.forEach(storageItem -> {
            final MediaFolder mediaFolder = mediaFolderService.findOrCreate(
                    providerId,
                    storageItem.path(),
                    storageItem.name(),
                    ingestSubtreeRequest.getMediaType()
            );

            final List<StorageItem> storageVideos = client.listChildVideos(storageItem.path());

            executorService.submit(() -> {
                try {
                    ingestionTaskService.createIngestionTask(
                            ingestion,
                            mediaFolder.id,
                            storageVideos
                    );
                } catch (RuntimeException e) {
                    Log.errorf(e, "Falha ao criar a tarefa de ingestão da pasta %d", mediaFolder.id);
                }
            });
        });

        Log.info("Criação de tarefas de ingestão concluída");

        return new IngestionStartedResponse().status(IngestionStartedResponse.StatusEnum.STARTED);
    }


    /**
     * Persiste a ingestão na sua própria transação, pra que ela já esteja commitada quando as
     * tasks, em outras threads, a referenciarem.
     *
     * @param ingestion ingestão nova, com {@code status} já preenchido; recebe o {@code id}
     *                  gerado
     */
    @Transactional
    public void createIngestion(Ingestion ingestion) {
        Ingestion.persist(ingestion);
    }

    /**
     * Remove do catalog tudo que foi ingerido sob um subtree do provider, numa transação só.
     *
     * <p>Remove primeiro o anime vinculado a cada pasta, porque a FK {@code anime.folder_id}
     * não tem cascade, e depois as pastas raiz do conjunto — o {@code ON DELETE CASCADE} leva
     * subpastas, arquivos e as {@code IngestionTask}s das pastas. Não checa se o provider
     * existe; sem pasta correspondente, não faz nada.
     *
     * @param providerId id do storage provider
     * @param rootPath subtree a remover; {@code null} ou vazio remove tudo que foi ingerido do
     *                 provider
     */
    @Transactional
    public void removeIngestedSubtree(Integer providerId, String rootPath) {
        final String normalizedRootPath = Objects.requireNonNullElse(rootPath, "");

        Log.infof("Removendo subtree ingerido do provider %d (rootPath=%s)", providerId, normalizedRootPath);

        final List<MediaFolder> folders = mediaFolderService.findSubtree(providerId, rootPath);

        for (MediaFolder folder : folders) {
            animeService.findByFolder(folder.id)
                    .map(anime -> anime.anilistId)
                    .ifPresent(animeService::deleteAnime);
        }

        mediaFolderService.deleteRoots(folders);
    }

    /**
     * Lista a árvore de pastas remotas do provider a partir de {@code rootPath}, marcando quais
     * já existem no catalog (via {@code MediaFolderService.findSubtree}).
     *
     * @param providerId id do storage provider
     * @param rootPath subtree a listar; {@code null} vira string vazia (árvore inteira do
     *                 provider) — se não vazio, precisa existir de fato no provider
     * @return a árvore de pastas remotas, cada nó marcando se já foi ingerido
     * @throws ResourceNotFoundException se o {@code providerId} não existir
     * @throws BusinessRuleException se {@code rootPath} não existir no provider
     * @throws IntegrationException se a listagem no provider remoto falhar (rclone rcd fora do
     *         ar, credenciais inválidas, timeout)
     */
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

        final String rootPathName = normalizedRootPath.isEmpty()
                ? ""
                : DirectoryUtils.lastSegmentOf(normalizedRootPath);

        return buildNode(
                normalizedRootPath,
                rootPathName,
                childrenByParentPath,
                ingestedPaths
        );
    }

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