package io.github.leonardopinheirolacerda.akari.domain.ingestion.model;

import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums.IngestionStep;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums.IngestionStepStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * Uma tentativa de executar uma {@link IngestionStep} para uma {@link IngestionTask},
 * persistida na tabela {@code ingestion_step_execution}.
 *
 * <p>Repetir uma etapa cria uma nova linha com {@code attempt} incrementado, preservando as
 * tentativas anteriores. Os eventos da tentativa ficam em {@link IngestionStepEvent}. Nenhum
 * fluxo cria execuções ainda.
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
}
