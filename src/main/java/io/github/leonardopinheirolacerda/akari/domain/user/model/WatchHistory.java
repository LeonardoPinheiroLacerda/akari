package io.github.leonardopinheirolacerda.akari.domain.user.model;

import io.github.leonardopinheirolacerda.akari.domain.videofile.model.VideoFile;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Progresso de um usuário num arquivo de vídeo — uma linha por par usuário/arquivo, atualizada a
 * cada heartbeat com posição de uma sessão de playback do usuário.
 */
@Entity
@Table(name = "user_watch_history")
public class WatchHistory extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    public User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id")
    public VideoFile videoFile;

    /** Última posição recebida, em segundos a partir do início da mídia. */
    @Column(name = "position_seconds")
    public Double positionSeconds;

    /** Duração da mídia segundo o probe; {@code null} se o probe não informou. */
    @Column(name = "duration_seconds")
    public Double durationSeconds;

    /** Chegou a 90% da duração alguma vez; nunca volta pra {@code false}. */
    public Boolean watched;

    @Column(name = "last_watched_at")
    public OffsetDateTime lastWatchedAt;

    /**
     * Busca o progresso de um usuário num arquivo.
     *
     * @param userId id do usuário
     * @param fileId id do arquivo
     * @return o progresso, se o usuário já abriu o arquivo
     */
    public static Optional<WatchHistory> findByUserAndFile(Integer userId, Integer fileId) {
        return WatchHistory
                .find("user.id = ?1 and videoFile.id = ?2", userId, fileId)
                .firstResultOptional();
    }

    /**
     * Progresso de um usuário em vários arquivos.
     *
     * @param userId id do usuário
     * @param fileIds ids dos arquivos
     * @return o progresso dos arquivos que o usuário já abriu; vazio se nenhum
     */
    public static List<WatchHistory> findByUserAndFiles(Integer userId, List<Integer> fileIds) {
        if (fileIds.isEmpty()) {
            return List.of();
        }

        return WatchHistory
                .list("user.id = ?1 and videoFile.id in ?2", userId, fileIds);
    }

}
