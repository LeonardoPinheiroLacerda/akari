package io.github.leonardopinheirolacerda.akari.domain.ingestion.service;

import io.github.leonardopinheirolacerda.akari.api.dto.*;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeOverrideService;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.mapper.IngestionMapper;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.Ingestion;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.IngestionStepEvent;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.IngestionStepExecution;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.IngestionTask;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.IngestionTaskStatusCount;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums.IngestionStatus;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums.IngestionStep;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums.IngestionTaskStatus;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.sse.IngestionEventBroadcaster;
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
import io.github.leonardopinheirolacerda.akari.model.PageResult;
import io.github.leonardopinheirolacerda.akari.utils.DirectoryUtils;
import io.quarkus.logging.Log;
import io.quarkus.virtual.threads.VirtualThreads;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * Ingestão de subtrees de storage provider no catalog: dispara a ingestão, lista as pastas
 * remotas marcando as já ingeridas, consulta o andamento das ingestões e publica esse andamento
 * no stream SSE ({@link IngestionEventBroadcaster}).
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
    IngestionMapper ingestionMapper;

    @Inject
    AnimeOverrideService animeOverrideService;

    @Inject
    IngestionEventBroadcaster ingestionEventBroadcaster;

    /**
     * Registra no catalog as pastas e os arquivos de vídeo de um subtree do provider e cria uma
     * {@link Ingestion} com uma {@code IngestionTask} por pasta com vídeo.
     *
     * <p>Fluxo: valida o TMDB configurado e o provider → lista a árvore remota a partir de
     * {@code rootPath}, acrescentando o próprio {@code rootPath} como pasta (o {@code listTree}
     * só devolve descendentes) → persiste a {@link Ingestion} como {@code PENDING} → pra cada
     * pasta, da mais rasa pra mais funda, cria a {@code MediaFolder} se não existir (transação
     * própria), lista os vídeos diretos dela e submete a criação da task numa thread virtual.
     * Só pasta com vídeo ganha task.
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
        publishIngestionUpdated(ingestion.id);

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

    /**
     * A ingestão em andamento ou, sem nenhuma em andamento, a mais recente, com todas as tasks.
     *
     * @return a ingestão; {@code null} (resposta {@code 204}) se nenhuma foi disparada ainda
     */
    public IngestionSummary getCurrentIngestion() {
        return Ingestion.findCurrent()
                .map(ingestion -> toSummary(ingestion, true))
                .orElse(null);
    }

    /**
     * Página de ingestões, da mais nova pra mais antiga, cada uma com todas as tasks.
     *
     * @param page número da página, 0-based
     * @param size tamanho da página
     * @return a página
     */
    public IngestionPage listIngestions(Integer page, Integer size) {
        final PageResult<Ingestion> pageResult = Ingestion.findPage(page, size);

        final List<IngestionSummary> summaries = pageResult.data()
                .stream()
                .map(ingestion -> toSummary(ingestion, true))
                .toList();

        return ingestionMapper.toPage(new PageResult<>(
                summaries,
                pageResult.page(),
                pageResult.size(),
                pageResult.totalElements(),
                pageResult.totalPages()
        ));
    }

    /**
     * Uma task com todas as execuções dos steps, cada uma com os seus eventos.
     *
     * @param taskId id da task
     * @return a task com a linha do tempo
     * @throws ResourceNotFoundException se o {@code taskId} não existir
     */
    public IngestionTaskDetail getIngestionTask(Integer taskId) {
        final IngestionTask task = IngestionTask.find(taskId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Não foi possível localizar uma tarefa de ingestão com o id informado"
                ));
        final List<IngestionStepExecution> executions = IngestionStepExecution.findByTask(taskId);

        final IngestionTaskDetail detail = ingestionMapper.toTaskDetail(task)
                .steps(latestSteps(executions))
                .executions(toExecutionDetails(executions));

        if (task.anime != null) {
            detail.title(animeOverrideService.resolveTitle(task.anime))
                    .thumbnail(animeOverrideService.resolveThumbnailMedium(task.anime));
        }

        return detail;
    }

    /**
     * Marca como resolvida manualmente uma task que espera curadoria, sem executar os steps que
     * não rodaram, e publica a mudança no stream.
     *
     * @param taskId id da task
     * @return a task, já {@code RESOLVED}, com a linha do tempo
     * @throws ResourceNotFoundException se o {@code taskId} não existir
     * @throws BusinessRuleException se a task não estiver em {@code WAITING_CURATION}
     */
    @Transactional
    public IngestionTaskDetail resolveIngestionTask(Integer taskId) {
        final IngestionTask task = IngestionTask.find(taskId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Não foi possível localizar uma tarefa de ingestão com o id informado"
                ));

        if (task.status != IngestionTaskStatus.WAITING_CURATION) {
            throw new BusinessRuleException(
                    "Só uma tarefa aguardando curadoria pode ser marcada como resolvida"
            );
        }

        task.status = IngestionTaskStatus.RESOLVED;

        Log.infof("Tarefa de ingestão %d marcada como resolvida manualmente", taskId);

        publishTaskUpdated(taskId);
        publishIngestionUpdated(task.ingestion.id);

        return getIngestionTask(taskId);
    }

    /**
     * Evento {@code SNAPSHOT} pra quem se inscreve no stream: a ingestão em andamento, ou a
     * última, com todas as tasks.
     *
     * @return o evento; {@code ingestion} é {@code null} se nenhuma foi disparada ainda
     */
    public IngestionStreamEvent getSnapshot() {
        return new IngestionStreamEvent()
                .type(IngestionStreamEventType.SNAPSHOT)
                .ingestion(getCurrentIngestion());
    }

    /**
     * Publica no stream o {@code INGESTION_UPDATED} da ingestão — status e contagens, sem as
     * tasks. Sem nenhum inscrito, nem monta o evento.
     *
     * @param ingestionId id da ingestão
     */
    public void publishIngestionUpdated(Integer ingestionId) {
        if (!ingestionEventBroadcaster.hasSubscribers()) {
            return;
        }

        final Ingestion ingestion = Ingestion.findById(ingestionId);

        ingestionEventBroadcaster.publish(
                new IngestionStreamEvent()
                        .type(IngestionStreamEventType.INGESTION_UPDATED)
                        .ingestion(toSummary(ingestion, false))
        );
    }

    /**
     * Publica no stream o {@code TASK_UPDATED} da task. Sem nenhum inscrito, nem monta o evento.
     *
     * @param taskId id da task
     */
    public void publishTaskUpdated(Integer taskId) {
        if (!ingestionEventBroadcaster.hasSubscribers()) {
            return;
        }

        final IngestionTask task = IngestionTask.find(taskId)
                .orElseThrow();

        ingestionEventBroadcaster.publish(
                new IngestionStreamEvent()
                        .type(IngestionStreamEventType.TASK_UPDATED)
                        .task(toTaskSummary(task, IngestionStepExecution.findByTask(taskId)))
        );
    }

    /**
     * Publica no stream o {@code STEP_FINISHED} da execução, com a task e todos os eventos da
     * execução. Sem nenhum inscrito, nem monta o evento.
     *
     * @param executionId id da execução que terminou
     */
    public void publishStepFinished(Integer executionId) {
        if (!ingestionEventBroadcaster.hasSubscribers()) {
            return;
        }

        final IngestionStepExecution execution = IngestionStepExecution.findById(executionId);
        final Integer taskId = execution.task.id;

        final IngestionTask task = IngestionTask.find(taskId)
                .orElseThrow();
        final List<IngestionStepExecution> executions = IngestionStepExecution.findByTask(taskId);

        ingestionEventBroadcaster.publish(
                new IngestionStreamEvent()
                        .type(IngestionStreamEventType.STEP_FINISHED)
                        .task(toTaskSummary(task, executions))
                        .execution(toExecutionDetails(List.of(execution)).getFirst())
        );
    }

    /**
     * Remove os sinks SSE já fechados pelo cliente. Chamado periodicamente pelo
     * {@code IngestionTaskScheduler}.
     */
    public void pruneClosedSseConnections() {
        final int removed = ingestionEventBroadcaster.pruneClosedSinks();

        if (removed > 0) {
            Log.debugf("%d conexões SSE de ingestão fechadas removidas", removed);
        }
    }

    /**
     * Converte a ingestão no resumo, com as contagens e a task em execução; com
     * {@code withTasks}, também todas as tasks.
     */
    private IngestionSummary toSummary(Ingestion ingestion, boolean withTasks) {
        final IngestionTaskSummary currentTask = IngestionTask.findRunning(ingestion.id)
                .map(task -> toTaskSummary(task, IngestionStepExecution.findByTask(task.id)))
                .orElse(null);

        final IngestionSummary summary = ingestionMapper.toSummary(ingestion)
                .taskCounts(toTaskCounts(ingestion.id))
                .currentTask(currentTask);

        if (!withTasks) {
            return summary;
        }

        final Map<Integer, List<IngestionStepExecution>> executionsByTask = IngestionStepExecution
                .findByIngestion(ingestion.id)
                .stream()
                .collect(Collectors.groupingBy(execution -> execution.task.id));

        final List<IngestionTaskSummary> tasks = IngestionTask.findByIngestion(ingestion.id)
                .stream()
                .map(task -> toTaskSummary(task, executionsByTask.getOrDefault(task.id, List.of())))
                .toList();

        return summary.tasks(tasks);
    }

    private IngestionTaskCounts toTaskCounts(Integer ingestionId) {
        final Map<IngestionTaskStatus, Integer> counts = IngestionTask.countByStatus(ingestionId)
                .stream()
                .collect(Collectors.toMap(
                        IngestionTaskStatusCount::status,
                        statusCount -> statusCount.count().intValue()
                ));

        final int total = counts.values()
                .stream()
                .mapToInt(Integer::intValue)
                .sum();

        return new IngestionTaskCounts()
                .total(total)
                .pending(counts.getOrDefault(IngestionTaskStatus.PENDING, 0))
                .running(counts.getOrDefault(IngestionTaskStatus.RUNNING, 0))
                .waitingCuration(counts.getOrDefault(IngestionTaskStatus.WAITING_CURATION, 0))
                .resolved(counts.getOrDefault(IngestionTaskStatus.RESOLVED, 0))
                .completed(counts.getOrDefault(IngestionTaskStatus.COMPLETED, 0))
                .failed(counts.getOrDefault(IngestionTaskStatus.FAILED, 0));
    }

    /**
     * Converte a task no resumo, com o título e o poster do anime resolvidos (override do TMDB,
     * se houver) e o status mais recente de cada step.
     *
     * @param executions execuções da task, na ordem em que rodaram
     */
    private IngestionTaskSummary toTaskSummary(IngestionTask task,
                                               List<IngestionStepExecution> executions) {

        final IngestionTaskSummary summary = ingestionMapper.toTaskSummary(task)
                .steps(latestSteps(executions));

        if (task.anime != null) {
            summary.title(animeOverrideService.resolveTitle(task.anime))
                    .thumbnail(animeOverrideService.resolveThumbnailMedium(task.anime));
        }

        return summary;
    }

    /**
     * Status da execução mais recente de cada step, na ordem dos steps — as execuções chegam na
     * ordem em que rodaram, então a última de cada step sobrescreve as anteriores.
     */
    private List<IngestionTaskStepStatus> latestSteps(List<IngestionStepExecution> executions) {
        final Map<IngestionStep, IngestionStepExecution> latestByStep =
                new EnumMap<>(IngestionStep.class);

        executions.forEach(execution -> latestByStep.put(execution.step, execution));

        return latestByStep.values()
                .stream()
                .map(ingestionMapper::toTaskStepStatus)
                .toList();
    }

    /**
     * Converte as execuções, cada uma com os seus eventos, buscados numa consulta só.
     */
    private List<IngestionStepExecutionDetail> toExecutionDetails(
            List<IngestionStepExecution> executions
    ) {
        if (executions.isEmpty()) {
            return List.of();
        }

        final List<Integer> executionIds = executions.stream()
                .map(execution -> execution.id)
                .toList();

        final Map<Integer, List<IngestionStepEventResponse>> eventsByExecution = IngestionStepEvent
                .findByExecutions(executionIds)
                .stream()
                .collect(Collectors.groupingBy(
                        event -> event.stepExecution.id,
                        Collectors.mapping(ingestionMapper::toEventResponse, Collectors.toList())
                ));

        return executions.stream()
                .map(execution -> ingestionMapper.toExecutionDetail(execution)
                        .events(eventsByExecution.getOrDefault(execution.id, List.of())))
                .toList();
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