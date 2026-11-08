package io.github.leonardopinheirolacerda.akari.domain.ingestion.service.step;

import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.IngestionStepEvent;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.IngestionStepExecution;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.IngestionTask;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums.IngestionEventType;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums.IngestionStep;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums.IngestionStepStatus;
import jakarta.transaction.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Base dos step services da ingestão — cada step estende esta classe sem conhecer os demais. O
 * {@code IngestionTaskExecutionService} chama os steps em sequência, cada um só se ainda não
 * tiver sido executado na task.
 *
 * <p>Também oferece aos steps o registro da própria execução ({@link IngestionStepExecution}) e
 * dos seus eventos ({@link IngestionStepEvent}): o {@link #start} devolve um {@link StepRun}, que
 * grava os eventos e fecha aquela execução. Cada gravação é uma transação curta: a linha do tempo
 * fica visível enquanto o step roda e nenhuma transação fica aberta durante as chamadas de rede
 * dele.
 */
public abstract class IngestionStepService {

    /**
     * Identifica o step implementado pela subclasse — usado pra localizar as execuções dele na
     * task.
     *
     * @return o step que a subclasse executa
     */
    protected abstract IngestionStep step();

    /**
     * Confirma se o step já foi executado até o fim na task — alguma execução terminou
     * {@code COMPLETED} ou {@code SKIPPED} —, pra que não rode de novo quando a task é retomada.
     * Um step com outra regra pode sobrescrever.
     *
     * @param task task em execução
     * @return {@code true} se o step não precisa rodar
     */
    @Transactional
    public boolean isExecuted(IngestionTask task) {
        return IngestionStepExecution.existsFinished(task.id, step());
    }

    /**
     * Executa o step na task, registrando a própria execução e seus eventos.
     *
     * @param task task em execução, com a pasta já carregada
     * @return status final do step: {@code COMPLETED} ou {@code SKIPPED} deixam a task seguir;
     *         {@code UNRESOLVED} para a task em {@code WAITING_CURATION} e {@code FAILED}, em
     *         {@code FAILED}
     */
    public abstract IngestionStepStatus execute(IngestionTask task);

    /**
     * Abre uma nova tentativa do step na task, como {@code RUNNING}, e marca o step como o atual
     * da task.
     *
     * @param taskId id da task
     * @return a execução aberta, por onde o step grava seus eventos e fecha a execução
     */
    @Transactional
    protected StepRun start(Integer taskId) {
        final IngestionStep step = step();

        final IngestionTask task = IngestionTask.findById(taskId);
        final IngestionStepExecution execution = new IngestionStepExecution();

        final long currentAttempts = IngestionStepExecution.countByTaskAndStep(taskId, step);

        execution.task = task;
        execution.step = step;
        execution.status = IngestionStepStatus.RUNNING;
        execution.attempt = (int) currentAttempts + 1;
        execution.startedAt = OffsetDateTime.now();

        execution.persist();

        task.currentStep = step;

        return new StepRun(this, execution);
    }

    /**
     * Grava um evento da execução — chamado pelos métodos do {@link StepRun}, um por tipo de
     * evento.
     *
     * @param stepExecution execução à qual o evento pertence
     * @param eventType tipo do evento
     * @param message descrição legível do evento
     * @param details dados estruturados do evento; {@code null} quando não há
     */
    @Transactional
    protected void record(
            IngestionStepExecution stepExecution,
            IngestionEventType eventType,
            String message,
            Map<String, Object> details
    ) {
        final IngestionStepEvent event = new IngestionStepEvent();

        event.stepExecution = IngestionStepExecution.findById(stepExecution.id);
        event.eventType = eventType;
        event.message = message;
        event.details = details;

        event.persist();
    }

    /**
     * Fecha a execução com o status final do step.
     *
     * <p>A execução recebida veio da transação do {@link #start} e está desanexada; por isso é
     * buscada de novo pelo id — alterar a recebida não seria gravado. Chamado pelo
     * {@link StepRun#finish}.
     *
     * @param stepExecution execução a fechar
     * @param status status final: {@code COMPLETED}, {@code SKIPPED}, {@code UNRESOLVED} ou
     *               {@code FAILED}
     */
    @Transactional
    protected void finish(IngestionStepExecution stepExecution, IngestionStepStatus status) {
        final IngestionStepExecution execution = IngestionStepExecution.findById(stepExecution.id);

        execution.status = status;
        execution.completedAt = OffsetDateTime.now();
    }
}
