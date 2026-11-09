package io.github.leonardopinheirolacerda.akari.domain.ingestion.model;

import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums.IngestionStep;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums.IngestionTaskStatus;
import io.github.leonardopinheirolacerda.akari.domain.mediafolder.model.MediaFolder;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * O processamento de uma pasta dentro de uma {@link Ingestion}, persistido na tabela
 * {@code ingestion_task}.
 *
 * <p>Criada depois que a pasta e os arquivos de vídeo diretos dela já estão no catalog. Cada
 * etapa executada para a pasta vira uma {@link IngestionStepExecution}. A task some junto com a
 * pasta ({@code ON DELETE CASCADE} em {@code folder_id}); a remoção do anime só limpa o vínculo
 * ({@code ON DELETE SET NULL} em {@code anime_id}).
 */
@Entity
@Table(name = "ingestion_task")
public class IngestionTask extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingestion_id", nullable = false)
    public Ingestion ingestion;

    /** Pasta processada pela task. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "folder_id", nullable = false)
    public MediaFolder folder;

    /** Anime vinculado à pasta pela etapa {@code ANILIST_SYNC}; {@code null} até o vínculo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anime_id", referencedColumnName = "anilist_id")
    public Anime anime;

    /**
     * Situação da task: {@code PENDING} na criação, {@code RUNNING} quando o scheduler a toma e
     * {@code COMPLETED}, {@code WAITING_CURATION} ou {@code FAILED} ao fim dos steps.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public IngestionTaskStatus status;

    /** Step em execução ou o último executado; {@code null} antes do primeiro. */
    @Enumerated(EnumType.STRING)
    @Column(name = "current_step")
    public IngestionStep currentStep;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    public OffsetDateTime createdAt;

    /** Quando o scheduler tomou a task pra execução; {@code null} até lá. */
    @Column(name = "started_at")
    public OffsetDateTime startedAt;

    /** Quando a task parou de ser processada, em qualquer situação final; {@code null} até lá. */
    @Column(name = "completed_at")
    public OffsetDateTime completedAt;

    /**
     * Busca a próxima task a executar — a {@code PENDING} ou {@code RUNNING} (interrompida por
     * uma queda do serviço) mais antiga —, já com a pasta carregada.
     *
     * @return a task; vazio se não houver nenhuma a executar
     */
    public static Optional<IngestionTask> findNextExecutable() {
        return IngestionTask
                .find(
                        "FROM IngestionTask t JOIN FETCH t.folder WHERE t.status IN ?1 ORDER BY t.id",
                        List.of(IngestionTaskStatus.PENDING, IngestionTaskStatus.RUNNING)
                )
                .firstResultOptional();
    }

    /**
     * Confere se a ingestão ainda tem task a executar ({@code PENDING} ou {@code RUNNING}).
     *
     * @param ingestionId id da ingestão
     * @return {@code true} se alguma task da ingestão ainda não terminou
     */
    public static boolean existsUnfinished(Integer ingestionId) {
        return count(
                "ingestion.id = ?1 and status in ?2",
                ingestionId,
                List.of(IngestionTaskStatus.PENDING, IngestionTaskStatus.RUNNING)
        ) > 0;
    }

    /**
     * Confere se alguma task da ingestão terminou {@code FAILED}.
     *
     * @param ingestionId id da ingestão
     * @return {@code true} se alguma task da ingestão falhou
     */
    public static boolean existsFailed(Integer ingestionId) {
        return count(
                "ingestion.id = ?1 and status = ?2",
                ingestionId,
                IngestionTaskStatus.FAILED
        ) > 0;
    }
}
