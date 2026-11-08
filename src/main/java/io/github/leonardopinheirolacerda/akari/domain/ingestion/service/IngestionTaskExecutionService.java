package io.github.leonardopinheirolacerda.akari.domain.ingestion.service;

import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeService;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.IngestionTask;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums.IngestionStepStatus;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums.IngestionTaskStatus;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.service.step.AnilistSyncStepService;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.service.step.IngestionStepService;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.service.step.TmdbBindStepService;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.service.step.TmdbFilePairStepService;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.service.step.TmdbOverridesStepService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Execução das {@link IngestionTask}s, uma por vez: roda a task inteira, chamando os steps
 * ({@link IngestionStepService}) em sequência — cada um só se ainda não tiver sido executado, o
 * que faz uma task interrompida retomar de onde parou.
 *
 * <p>Uma por vez, não em paralelo: tasks da mesma franquia rodando juntas disputam as arestas de
 * {@code anime_relation} no {@code syncRelations} do bind (chave duplicada, deadlock).
 */
@ApplicationScoped
public class IngestionTaskExecutionService {

    @Inject
    AnilistSyncStepService anilistSyncStepService;

    @Inject
    TmdbBindStepService tmdbBindStepService;

    @Inject
    TmdbOverridesStepService tmdbOverridesStepService;

    @Inject
    TmdbFilePairStepService tmdbFilePairStepService;

    @Inject
    AnimeService animeService;

    /**
     * Pega a próxima task a executar e roda os steps dela em sequência, pulando os já executados.
     * Um step que termina {@code UNRESOLVED} para a task em {@code WAITING_CURATION}, e um que
     * termina {@code FAILED}, em {@code FAILED}; se todos passarem, a task termina
     * {@code COMPLETED}. Chamado pelo
     * {@code IngestionTaskScheduler}, que pula os ciclos seguintes enquanto a task não acaba.
     */
    public void executeNextTask() {
        final Optional<IngestionTask> next = claimNextTask();

        if (next.isEmpty()) {
            return;
        }

        final IngestionTask task = next.get();

        try {
            final List<IngestionStepService> steps = List.of(
                    anilistSyncStepService,
                    tmdbBindStepService,
                    tmdbOverridesStepService,
                    tmdbFilePairStepService
            );

            for (IngestionStepService step : steps) {
                if (step.isExecuted(task)) {
                    continue;
                }

                final IngestionStepStatus status = step.execute(task);

                if (status == IngestionStepStatus.UNRESOLVED) {
                    finishTask(task.id, IngestionTaskStatus.WAITING_CURATION);
                    return;
                }

                if (status == IngestionStepStatus.FAILED) {
                    finishTask(task.id, IngestionTaskStatus.FAILED);
                    return;
                }
            }

            finishTask(task.id, IngestionTaskStatus.COMPLETED);
        } catch (RuntimeException e) {
            Log.errorf(e, "Tarefa de ingestão %d falhou de forma inesperada", task.id);
            finishTask(task.id, IngestionTaskStatus.FAILED);
        }
    }

    /**
     * Marca como {@code RUNNING} a próxima task a executar — uma {@code RUNNING} no início de um
     * ciclo só pode ter sobrado de uma queda do serviço, já que a task anterior sempre termina
     * antes.
     *
     * @return a task, com a pasta carregada; vazio se não houver task a executar
     */
    @Transactional
    Optional<IngestionTask> claimNextTask() {
        final Optional<IngestionTask> task = IngestionTask.findNextExecutable();

        task.ifPresent(claimed -> {
            claimed.status = IngestionTaskStatus.RUNNING;

            if (claimed.startedAt == null) {
                claimed.startedAt = OffsetDateTime.now();
            }
        });

        return task;
    }

    /**
     * Grava a situação final da task, quando ela parou de ser processada e o anime vinculado à
     * pasta, se houver.
     *
     * @param taskId id da task
     * @param status {@code COMPLETED}, {@code WAITING_CURATION} ou {@code FAILED}
     */
    @Transactional
    void finishTask(Integer taskId, IngestionTaskStatus status) {
        final IngestionTask task = IngestionTask.findById(taskId);

        task.status = status;
        task.completedAt = OffsetDateTime.now();
        task.anime = animeService.findByFolder(task.folder.id)
                .orElse(null);

        Log.infof("Tarefa de ingestão %d terminou como %s", taskId, status);
    }
}
