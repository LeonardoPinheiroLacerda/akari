package io.github.leonardopinheirolacerda.akari.domain.ingestion.model;

import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums.IngestionStep;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums.IngestionTaskStatus;
import io.github.leonardopinheirolacerda.akari.domain.mediafolder.model.MediaFolder;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

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

    /** Situação da task; gravada como {@code PENDING} na criação. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public IngestionTaskStatus status;

    /** Etapa em execução ou a última executada; {@code null} antes da primeira. */
    @Enumerated(EnumType.STRING)
    @Column(name = "current_step")
    public IngestionStep currentStep;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    public OffsetDateTime createdAt;

    /** Quando a primeira etapa começou; {@code null} até lá. */
    @Column(name = "started_at")
    public OffsetDateTime startedAt;

    /** Quando a última etapa terminou; {@code null} até lá. */
    @Column(name = "completed_at")
    public OffsetDateTime completedAt;
}
