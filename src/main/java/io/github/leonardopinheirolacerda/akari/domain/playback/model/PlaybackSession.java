package io.github.leonardopinheirolacerda.akari.domain.playback.model;

import io.github.leonardopinheirolacerda.akari.domain.videofile.model.VideoFile;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Sessão de transcode HLS de um {@link VideoFile}, persistida na tabela {@code playback_session}.
 *
 * <p>100% efêmera — não tem um status "encerrada" persistido; o estado é inferido pela
 * combinação dos campos, e a linha é apagada (não marcada) quando a sessão termina, seja por
 * reinício (mesma linha, novo processo) ou pela varredura de expiração.
 */
@Entity
@Table(name = "playback_session")
public class PlaybackSession extends PanacheEntityBase {

    /** Também nomeia o diretório de trabalho ({@code workDir}) em disco. */
    @Id
    @Column(name = "session_id")
    public UUID sessionId;

    /** Arquivo sendo reproduzido; removido em cascata se o arquivo for excluído. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id")
    public VideoFile videoFile;

    /** Nome de um {@code Profile}. */
    public String profile;

    /** Posição (0-based) no array {@code audio} do probe — não o índice global do ffprobe. */
    @Column(name = "audio_track_index")
    public Integer audioTrackIndex;

    /** Posição (0-based) no array {@code subtitles} do probe; {@code null} = sem legenda. */
    @Column(name = "subtitle_track_index")
    public Integer subtitleTrackIndex;

    /** Pid do processo ffmpeg atual; {@code null} se nenhum estiver rodando. */
    @Column(name = "ffmpeg_pid")
    public Long ffmpegPid;

    /** Caminho absoluto do diretório com a playlist e os segmentos desta sessão. */
    @Column(name = "work_dir")
    public String workDir;

    /** Instante de mídia correspondente ao {@code currentTime 0} da playlist atual. */
    @Column(name = "start_offset_seconds")
    public Double startOffsetSeconds;

    /**
     * Quando o processo ffmpeg atual começou — usado para confirmar que o {@code ffmpegPid}
     * ainda pertence a esse processo antes de matá-lo (o SO pode reciclar o pid).
     */
    @Column(name = "process_started_at")
    public OffsetDateTime processStartedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public OffsetDateTime createdAt;

    /** Último heartbeat recebido; {@code null} até o primeiro. */
    @Column(name = "last_heartbeat_at")
    public OffsetDateTime lastHeartbeatAt;

    /**
     * Confirma se a sessão expirou por falta de heartbeat.
     *
     * @param now instante de referência
     * @param ttl tempo máximo sem heartbeat antes de expirar
     * @return {@code true} se {@code now} já passou do último heartbeat (ou da criação, se
     *         nunca houve heartbeat) mais o {@code ttl}
     */
    public boolean isExpired(OffsetDateTime now, Duration ttl) {
        final OffsetDateTime lastSeen = lastHeartbeatAt != null
                ? lastHeartbeatAt
                : createdAt;

        return now.isAfter(lastSeen.plus(ttl));
    }

    /**
     * Busca uma sessão pelo id.
     *
     * @param sessionId id da sessão
     * @return a sessão; vazio se não existe
     */
    public static Optional<PlaybackSession> find(UUID sessionId) {
        return PlaybackSession
                .find("sessionId = ?1", sessionId)
                .firstResultOptional();
    }

}
