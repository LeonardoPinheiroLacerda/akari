package io.github.leonardopinheirolacerda.akari.domain.ingestion.model;

import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums.IngestionStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * Uma execução de ingestão de um subtree de storage provider, persistida na tabela
 * {@code ingestion}.
 *
 * <p>Agrupa as {@link IngestionTask}s criadas no disparo, uma por pasta do subtree. Remover a
 * ingestão remove as tasks junto ({@code ON DELETE CASCADE} em {@code ingestion_task}).
 */
@Entity
@Table(name = "ingestion")
public class Ingestion extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;

    /** Situação agregada da ingestão; gravada como {@code PENDING} na criação. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public IngestionStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    public OffsetDateTime createdAt;

    /** Quando a primeira task começou a ser processada; {@code null} até lá. */
    @Column(name = "started_at")
    public OffsetDateTime startedAt;

    /** Quando a última task terminou; {@code null} até lá. */
    @Column(name = "completed_at")
    public OffsetDateTime completedAt;
}
