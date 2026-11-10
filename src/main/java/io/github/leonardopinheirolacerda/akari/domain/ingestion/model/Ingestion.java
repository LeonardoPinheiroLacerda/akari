package io.github.leonardopinheirolacerda.akari.domain.ingestion.model;

import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums.IngestionStatus;
import io.github.leonardopinheirolacerda.akari.model.PageResult;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.Optional;

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

    /**
     * Situação agregada da ingestão: {@code PENDING} na criação, {@code RUNNING} quando a primeira
     * task começa, e {@code COMPLETED} ou {@code COMPLETED_WITH_ERRORS} quando a última termina.
     */
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

    /**
     * A ingestão em andamento ({@code RUNNING}) ou, sem nenhuma em andamento, a mais recente.
     *
     * @return a ingestão; vazio se nenhuma foi disparada ainda
     */
    public static Optional<Ingestion> findCurrent() {
        return Ingestion
                .<Ingestion>find("status = ?1 order by id desc", IngestionStatus.RUNNING)
                .firstResultOptional()
                .or(() -> Ingestion
                        .<Ingestion>find("order by id desc")
                        .firstResultOptional());
    }

    /**
     * Página de ingestões, da mais nova pra mais antiga.
     *
     * @param page número da página, 0-based
     * @param size tamanho da página
     * @return a página
     */
    public static PageResult<Ingestion> findPage(Integer page, Integer size) {
        final PanacheQuery<Ingestion> query = Ingestion
                .<Ingestion>find("order by id desc")
                .page(page, size);

        return new PageResult<>(
                query.list(),
                page,
                size,
                query.count(),
                query.pageCount()
        );
    }
}
