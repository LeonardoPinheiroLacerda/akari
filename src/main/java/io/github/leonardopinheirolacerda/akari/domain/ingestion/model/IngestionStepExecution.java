package io.github.leonardopinheirolacerda.akari.domain.ingestion.model;

import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums.IngestionStep;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums.IngestionStepStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Uma tentativa de executar uma {@link IngestionStep} para uma {@link IngestionTask},
 * persistida na tabela {@code ingestion_step_execution}.
 *
 * <p>Repetir uma etapa cria uma nova linha com {@code attempt} incrementado, preservando as
 * tentativas anteriores. Os eventos da tentativa ficam em {@link IngestionStepEvent}. Criada e
 * finalizada pelo próprio step, via {@code IngestionStepService}.
 */
@Entity
@Table(name = "ingestion_step_execution")
public class IngestionStepExecution extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    public IngestionTask task;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public IngestionStep step;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public IngestionStepStatus status;

    /** Número da tentativa dessa etapa na task, a partir de 1 (garantido por {@code CHECK}). */
    @Column(nullable = false)
    public Integer attempt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    public OffsetDateTime createdAt;

    /** Quando a tentativa começou; {@code null} enquanto {@code PENDING}. */
    @Column(name = "started_at")
    public OffsetDateTime startedAt;

    /** Quando a tentativa terminou; {@code null} até lá. */
    @Column(name = "completed_at")
    public OffsetDateTime completedAt;

    /**
     * Conta as tentativas já registradas de uma etapa numa task — base do {@code attempt} da
     * próxima tentativa.
     *
     * @param taskId id da task
     * @param step etapa contada
     * @return quantas execuções dessa etapa a task já tem; {@code 0} se nenhuma
     */
    public static long countByTaskAndStep(Integer taskId, IngestionStep step) {
        return IngestionStepExecution.count("task.id = ?1 AND step = ?2", taskId, step);
    }

    /**
     * Confirma se a etapa já foi executada até o fim na task — alguma tentativa terminou
     * {@code COMPLETED} ou {@code SKIPPED}.
     *
     * @param taskId id da task
     * @param step etapa verificada
     * @return {@code true} se a etapa não precisa rodar de novo
     */
    public static boolean existsFinished(Integer taskId, IngestionStep step) {
        return IngestionStepExecution
                .find(
                        "task.id = ?1 AND step = ?2 AND status IN ?3",
                        taskId,
                        step,
                        List.of(IngestionStepStatus.COMPLETED, IngestionStepStatus.SKIPPED)
                )
                .firstResultOptional()
                .isPresent();
    }

    /**
     * Execuções da task, na ordem em que rodaram.
     *
     * @param taskId id da task
     * @return as execuções, com a task carregada
     */
    public static List<IngestionStepExecution> findByTask(Integer taskId) {
        return IngestionStepExecution
                .<IngestionStepExecution>find(
                        "FROM IngestionStepExecution e JOIN FETCH e.task WHERE e.task.id = ?1"
                                + " ORDER BY e.id",
                        taskId
                )
                .list();
    }

    /**
     * Execuções de todas as tasks da ingestão, na ordem em que rodaram, numa consulta só.
     *
     * @param ingestionId id da ingestão
     * @return as execuções, com a task carregada
     */
    public static List<IngestionStepExecution> findByIngestion(Integer ingestionId) {
        return IngestionStepExecution
                .<IngestionStepExecution>find(
                        "FROM IngestionStepExecution e JOIN FETCH e.task"
                                + " WHERE e.task.ingestion.id = ?1 ORDER BY e.id",
                        ingestionId
                )
                .list();
    }
}
