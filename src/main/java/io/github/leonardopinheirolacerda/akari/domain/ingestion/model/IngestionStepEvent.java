package io.github.leonardopinheirolacerda.akari.domain.ingestion.model;

import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums.IngestionEventType;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Um evento da linha do tempo de uma {@link IngestionStepExecution}, persistido na tabela
 * {@code ingestion_step_event}.
 *
 * <p>Some junto com a execução ({@code ON DELETE CASCADE}). Gravado pelos steps via
 * {@code IngestionStepService}, um por transação.
 */
@Entity
@Table(name = "ingestion_step_event")
public class IngestionStepEvent extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "step_execution_id", nullable = false)
    public IngestionStepExecution stepExecution;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    public IngestionEventType eventType;

    /** Descrição legível do evento. */
    @Column(columnDefinition = "TEXT")
    public String message;

    /** Dados estruturados do evento, sem schema fixo, gravados como {@code JSONB}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB")
    public Map<String, Object> details;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    public OffsetDateTime createdAt;

    /**
     * Eventos das execuções, na ordem em que foram gravados, numa consulta só.
     *
     * @param executionIds ids das execuções
     * @return os eventos, com a execução carregada
     */
    public static List<IngestionStepEvent> findByExecutions(List<Integer> executionIds) {
        return IngestionStepEvent
                .<IngestionStepEvent>find(
                        "FROM IngestionStepEvent e JOIN FETCH e.stepExecution"
                                + " WHERE e.stepExecution.id IN ?1 ORDER BY e.id",
                        executionIds
                )
                .list();
    }
}
