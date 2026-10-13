package io.github.leonardopinheirolacerda.akari.domain.playback.service;

import io.github.leonardopinheirolacerda.akari.api.dto.AudioTrack;
import io.github.leonardopinheirolacerda.akari.api.dto.MediaInfoResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.PlaybackSessionResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.SeekRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.SessionHeartbeat;
import io.github.leonardopinheirolacerda.akari.api.dto.StartPlaybackSessionRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.SubtitleTrack;
import io.github.leonardopinheirolacerda.akari.api.dto.SwitchTracksRequest;
import io.github.leonardopinheirolacerda.akari.domain.mediaprobe.service.MediaProbeService;
import io.github.leonardopinheirolacerda.akari.domain.playback.client.FfmpegClient;
import io.github.leonardopinheirolacerda.akari.domain.playback.client.FfmpegException;
import io.github.leonardopinheirolacerda.akari.domain.playback.client.model.FfmpegStartParams;
import io.github.leonardopinheirolacerda.akari.domain.playback.mapper.PlaybackMapper;
import io.github.leonardopinheirolacerda.akari.domain.playback.model.PlaybackSession;
import io.github.leonardopinheirolacerda.akari.domain.playback.parser.HlsPlaylistParser;
import io.github.leonardopinheirolacerda.akari.domain.playback.transcode.HlsTranscodeStrategy;
import io.github.leonardopinheirolacerda.akari.domain.playback.transcode.Profile;
import io.github.leonardopinheirolacerda.akari.domain.playback.transcode.TranscodeProfileSelector;
import io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec.HlsTranscodeSpec;
import io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec.HwAccel;
import io.github.leonardopinheirolacerda.akari.domain.setting.service.SettingService;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.StorageProviderClientFactory;
import io.github.leonardopinheirolacerda.akari.domain.videofile.model.VideoFile;
import io.github.leonardopinheirolacerda.akari.domain.videofile.service.VideoFileService;
import io.github.leonardopinheirolacerda.akari.exceptions.BusinessRuleException;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationNotConfiguredException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.github.leonardopinheirolacerda.akari.utils.VideoFileLocationUtils;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Sessões de reprodução de mídia: inicia, reposiciona, troca faixas e encerra transcodes HLS
 * sob demanda via {@code ffmpeg}.
 *
 * <p>100% efêmero — nada além da linha de {@link PlaybackSession} é persistido entre chamadas;
 * o estado real (processo rodando, arquivos HLS) vive em disco e é descartado quando a sessão
 * termina, seja por reinício (mesma sessão, processo novo) ou pela varredura de expiração
 * ({@code PlaybackScheduler}).
 */
@ApplicationScoped
public class PlaybackService {

    private static final double SEEK_WINDOW_TOLERANCE_SECONDS = 0.5;

    /**
     * Formatos de nome de arquivo HLS que uma sessão pode produzir: segmento de rendition única
     * ({@code segment_N.ts}/{@code .m4s}), segmento de variant/soft-subtitle
     * ({@code segment_V_N.ts}/{@code .m4s}), playlist única ({@code index.m3u8}), master
     * playlist ({@code master.m3u8}), variant playlist ({@code stream_V.m3u8}), playlist da
     * legenda WebVTT ({@code stream_V_vtt.m3u8}), segmento de legenda WebVTT ({@code stream_VN.vtt}
     * — o ffmpeg concatena a variante e o número do segmento sem separador, confirmado rodando um
     * transcode real com legenda soft) e o segmento de inicialização do fMP4
     * ({@code init.mp4}/{@code init_V.mp4}, nenhuma estratégia usa {@code HlsSegmentType.FMP4}
     * hoje — mesma situação do legado). Qualquer outro nome é rejeitado — protege contra path
     * traversal.
     */
    private static final Pattern ALLOWED_MEDIA_FILE_NAME = Pattern.compile(
            "^(index|master)\\.m3u8$"
                    + "|^segment_\\d+\\.(ts|m4s)$"
                    + "|^segment_\\d+_\\d+\\.(ts|m4s)$"
                    + "|^stream_\\d+\\.m3u8$"
                    + "|^stream_\\d+_vtt\\.m3u8$"
                    + "|^stream_\\d+\\.vtt$"
                    + "|^init(_\\d+)?\\.mp4$"
    );

    @Inject
    VideoFileService videoFileService;

    @Inject
    MediaProbeService mediaProbeService;

    @Inject
    StorageProviderClientFactory storageProviderClientFactory;

    @Inject
    SettingService settingService;

    @Inject
    FfmpegClient ffmpegClient;

    @Inject
    ProcessTerminationService processTerminationService;

    @Inject
    PlaybackWorkspaceService playbackWorkspaceService;

    @Inject
    TranscodeProfileSelector transcodeProfileSelector;

    @Inject
    PlaybackMapper playbackMapper;

    /**
     * Inicia uma nova sessão de transcode HLS.
     *
     * @param request arquivo, profile e faixas de áudio/legenda escolhidas
     * @return a sessão recém-criada, com {@code startOffsetSeconds=0}
     * @throws ResourceNotFoundException se o {@code fileId} não existir no catalog, ou se o
     *         arquivo de provider remoto ainda não tiver terminado de baixar no cache
     * @throws BusinessRuleException se o {@code profile} informado for desconhecido, se nenhuma
     *         estratégia de transcode se aplicar a ele, ou se os índices de faixa não existirem
     *         no arquivo
     * @throws IntegrationNotConfiguredException se {@code playback.work.base-dir} não estiver
     *         configurado
     * @throws IntegrationException se o ffmpeg falhar ao iniciar
     */
    @Transactional
    public PlaybackSessionResponse startPlaybackSession(StartPlaybackSessionRequest request) {
        final VideoFile videoFile = videoFileService.findOrThrow(request.getFileId());
        final Profile profile = resolveProfile(request.getProfile());
        final Path inputFile = VideoFileLocationUtils.resolvePath(
                videoFile,
                storageProviderClientFactory,
                settingService.getCacheDir()
        );
        final MediaInfoResponse mediaInfo = mediaProbeService.getMediaProbe(videoFile.id);

        validateTrackBounds(mediaInfo, request.getAudioTrackIndex(), request.getSubtitleTrackIndex());

        final HlsTranscodeSpec spec = resolveSpec(profile, mediaInfo, videoFile.id);

        final UUID sessionId = UUID.randomUUID();
        final Path workDir = createWorkDir(sessionId);

        final Long jobId = startFfmpeg(
                inputFile,
                workDir,
                0.0,
                request.getAudioTrackIndex(),
                request.getSubtitleTrackIndex(),
                mediaInfo,
                spec
        );

        final PlaybackSession session = new PlaybackSession();
        final OffsetDateTime now = OffsetDateTime.now();

        session.sessionId = sessionId;
        session.videoFile = videoFile;
        session.profile = profile.name();
        session.audioTrackIndex = request.getAudioTrackIndex();
        session.subtitleTrackIndex = request.getSubtitleTrackIndex();
        session.ffmpegPid = jobId;
        session.workDir = workDir.toString();
        session.startOffsetSeconds = 0.0;
        session.processStartedAt = now;
        session.lastHeartbeatAt = now;

        session.persist();

        Log.infof("Sessão de playback %s iniciada pro arquivo %d", sessionId, videoFile.id);

        return playbackMapper.toResponse(session);
    }

    /**
     * Reposiciona o transcode de uma sessão já iniciada.
     *
     * <p>Se o instante alvo já estiver dentro da janela já transcodificada, não reinicia o
     * processo — o player já tem esse trecho em buffer. Fora da janela, reinicia o ffmpeg
     * reusando as faixas de áudio/legenda já ativas na sessão.
     *
     * @param sessionId id da sessão
     * @param request instante alvo
     * @return a sessão atualizada (mesmo {@code sessionId}, possivelmente novo
     *         {@code startOffsetSeconds} se houve reinício)
     * @throws ResourceNotFoundException se a sessão não existir ou já tiver expirado
     */
    @Transactional
    public PlaybackSessionResponse seekPlaybackSession(UUID sessionId, SeekRequest request) {
        final PlaybackSession session = findActiveOrThrow(sessionId);

        final Path durationPlaylist = resolveDurationPlaylistFile(session);

        final double transcodedSeconds = HlsPlaylistParser.sumExtinfSeconds(durationPlaylist);

        final double windowStart = session.startOffsetSeconds - SEEK_WINDOW_TOLERANCE_SECONDS;
        final double windowEnd = session.startOffsetSeconds + transcodedSeconds + SEEK_WINDOW_TOLERANCE_SECONDS;

        if (request.getPositionSeconds() >= windowStart && request.getPositionSeconds() <= windowEnd) {
            return playbackMapper.toResponse(session);
        }

        final PlaybackSession response = restart(
                session,
                request.getPositionSeconds(),
                session.audioTrackIndex,
                session.subtitleTrackIndex
        );

        return playbackMapper.toResponse(response);
    }

    /**
     * Troca a faixa de áudio e/ou legenda de uma sessão já iniciada, sempre reiniciando o
     * transcode — ao contrário do {@code seek}, não há posição que evite o reinício, já que
     * trocar {@code -map}/filtro de legenda exige um novo processo ffmpeg.
     *
     * @param sessionId id da sessão
     * @param request instante de retomada e as novas faixas de áudio/legenda
     * @return a sessão atualizada (mesmo {@code sessionId}, novo {@code startOffsetSeconds})
     * @throws ResourceNotFoundException se a sessão não existir ou já tiver expirado
     * @throws BusinessRuleException se os novos índices de faixa não existirem no arquivo
     */
    @Transactional
    public PlaybackSessionResponse switchPlaybackSessionTracks(UUID sessionId, SwitchTracksRequest request) {
        final PlaybackSession session = findActiveOrThrow(sessionId);

        final PlaybackSession response = restart(
                session,
                request.getPositionSeconds(),
                request.getAudioTrackIndex(),
                request.getSubtitleTrackIndex()
        );

        return playbackMapper.toResponse(response);
    }

    /**
     * Devolve o conteúdo da playlist HLS da sessão.
     *
     * @param sessionId id da sessão
     * @return a playlist {@code master.m3u8} (sessão com legenda soft) ou {@code index.m3u8}
     *         (demais casos), como texto
     * @throws ResourceNotFoundException se a sessão não existir, tiver expirado, ou a playlist
     *         ainda não tiver sido escrita (transcode ainda não produziu o primeiro segmento)
     */
    public String getPlaybackPlaylist(UUID sessionId) {
        final PlaybackSession session = findActiveOrThrow(sessionId);
        final Path playlistFile = resolveClientPlaylistFile(session);

        if (!Files.isRegularFile(playlistFile)) {
            throw new ResourceNotFoundException("A playlist desta sessão ainda não foi gerada");
        }

        try {
            return Files.readString(playlistFile);
        } catch (IOException e) {
            throw new IntegrationException("Falha ao ler a playlist da sessão " + sessionId, e);
        }
    }

    /**
     * Devolve um arquivo HLS referenciado pela playlist da sessão (segmento, variant playlist
     * ou legenda WebVTT).
     *
     * @param sessionId id da sessão
     * @param fileName nome do arquivo, exatamente como referenciado na playlist
     * @return o arquivo, pronto pra ser servido
     * @throws ResourceNotFoundException se a sessão não existir, tiver expirado, o nome não
     *         seguir o formato esperado, ou o arquivo ainda não tiver sido gerado
     */
    public File getPlaybackMediaFile(UUID sessionId, String fileName) {
        final PlaybackSession session = findActiveOrThrow(sessionId);

        if (fileName == null || !ALLOWED_MEDIA_FILE_NAME.matcher(fileName).matches()) {
            throw new ResourceNotFoundException("Nome de arquivo não reconhecido pela sessão");
        }

        final Path file = Path.of(session.workDir)
                .resolve(fileName);

        if (!Files.isRegularFile(file)) {
            throw new ResourceNotFoundException("Arquivo ainda não gerado pela sessão");
        }

        return file.toFile();
    }

    /**
     * Renova o TTL de uma sessão ativa.
     *
     * @param sessionId id da sessão
     * @return quanto tempo falta até a sessão expirar sem um novo heartbeat
     * @throws ResourceNotFoundException se a sessão não existir ou já tiver expirado (não
     *         revive uma sessão expirada)
     */
    @Transactional
    public SessionHeartbeat heartbeatPlaybackSession(UUID sessionId) {
        final PlaybackSession session = findActiveOrThrow(sessionId);

        session.lastHeartbeatAt = OffsetDateTime.now();

        final Duration playbackSessionHeartbeatTtlSetting = settingService.getPlaybackSessionHeartbeatTtl();
        final long seconds = playbackSessionHeartbeatTtlSetting.toSeconds();

        return new SessionHeartbeat(seconds);
    }

    /**
     * Devolve o TTL de heartbeat configurado, em segundos.
     *
     * @return o TTL da preferência {@code playback.session.heartbeat-ttl}
     */
    public Long getHeartbeatTtlSeconds() {
        return settingService.getPlaybackSessionHeartbeatTtl().toSeconds();
    }

    /**
     * Encerra e remove toda sessão que não recebeu heartbeat dentro do TTL configurado.
     *
     * <p>Cada sessão é tratada isoladamente — uma falha ao encerrar uma não impede as demais de
     * serem varridas na mesma chamada.
     */
    @Transactional
    public void sweepExpiredSessions() {
        final Duration ttl = settingService.getPlaybackSessionHeartbeatTtl();
        final OffsetDateTime now = OffsetDateTime.now();

        final List<PlaybackSession> sessions = PlaybackSession.listAll();

        for (PlaybackSession session : sessions) {
            if (!session.isExpired(now, ttl)) {
                continue;
            }

            try {
                Log.infof("Sessão de playback %s expirou, encerrando", session.sessionId);
                terminateAndCleanup(session);
                session.delete();
            } catch (Exception e) {
                Log.errorf(e, "Falha ao encerrar a sessão de playback %s expirada", session.sessionId);
            }
        }
    }

    private PlaybackSession findActiveOrThrow(UUID sessionId) {
        final PlaybackSession session = PlaybackSession.find(sessionId)
                .orElseThrow(() -> {
                    Log.warnf("Sessão de playback %s não encontrada", sessionId);
                    return new ResourceNotFoundException("Não foi possível localizar uma sessão de playback com o id informado");
                });

        final Duration ttl = settingService.getPlaybackSessionHeartbeatTtl();

        if (session.isExpired(OffsetDateTime.now(), ttl)) {
            Log.warnf("Sessão de playback %s já expirou", sessionId);
            throw new ResourceNotFoundException("Esta sessão de playback já expirou");
        }

        return session;
    }

    private PlaybackSession restart(PlaybackSession session,
                                    Double positionSeconds,
                                    Integer audioTrackIndex,
                                    Integer subtitleTrackIndex) {

        final VideoFile videoFile = videoFileService.findOrThrow(session.videoFile.id);
        final Path inputFile = VideoFileLocationUtils.resolvePath(videoFile, storageProviderClientFactory, settingService.getCacheDir());
        final MediaInfoResponse mediaInfo = mediaProbeService.getMediaProbe(videoFile.id);

        validateTrackBounds(mediaInfo, audioTrackIndex, subtitleTrackIndex);
        final Profile profile = resolveProfile(session.profile);
        final HlsTranscodeSpec spec = resolveSpec(profile, mediaInfo, videoFile.id);

        terminateAndCleanup(session);

        final Path workDir = createWorkDir(session.sessionId);
        final Long jobId = startFfmpeg(
                inputFile,
                workDir,
                positionSeconds,
                audioTrackIndex,
                subtitleTrackIndex,
                mediaInfo,
                spec
        );

        session.ffmpegPid = jobId;
        session.processStartedAt = OffsetDateTime.now();
        session.startOffsetSeconds = positionSeconds;
        session.audioTrackIndex = audioTrackIndex;
        session.subtitleTrackIndex = subtitleTrackIndex;

        Log.infof("Sessão de playback %s reiniciada no instante %s", session.sessionId, positionSeconds);

        return session;
    }

    private Long startFfmpeg(Path inputFile,
                             Path workDir,
                             Double startOffsetSeconds,
                             Integer audioTrackIndex,
                             Integer subtitleTrackIndex,
                             MediaInfoResponse mediaInfo,
                             HlsTranscodeSpec spec) {

        final String hwAccelSetting = settingService.getPlaybackTranscodeHardwareAcceleration();
        final HwAccel hwAccel = HwAccel.fromSetting(hwAccelSetting);

        final FfmpegStartParams params = new FfmpegStartParams(
                spec,
                inputFile,
                workDir,
                startOffsetSeconds,
                audioTrackIndex,
                subtitleTrackIndex,
                mediaInfo,
                hwAccel
        );

        try {
            return ffmpegClient.start(params);

        } catch (FfmpegException e) {
            Log.errorf(e, "Falha ao iniciar o ffmpeg em %s", workDir);
            throw new IntegrationException(e.getMessage(), e);
        }
    }

    private Profile resolveProfile(String profileName) {
        try {
            final String profile = profileName.trim().toUpperCase(Locale.ROOT);
            return Profile.valueOf(profile);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessRuleException("Profile de transcode desconhecido: '" + profileName + "'. Valores aceitos: WEB, MOBILE, TV.");
        }
    }

    private HlsTranscodeSpec resolveSpec(Profile profile, MediaInfoResponse mediaInfo, Integer fileId) {
        final HlsTranscodeStrategy strategy = transcodeProfileSelector.select(profile, mediaInfo)
                .orElseThrow(() -> new BusinessRuleException(
                        "Nenhuma estratégia de transcode aplicável ao profile " + profile + " para o arquivo " + fileId));

        return strategy.buildSpec(mediaInfo);
    }

    private void validateTrackBounds(MediaInfoResponse mediaInfo, Integer audioTrackIndex, Integer subtitleTrackIndex) {
        final List<AudioTrack> audioTracks = mediaInfo.getAudio();

        if (audioTrackIndex < 0 || audioTrackIndex >= audioTracks.size()) {
            throw new BusinessRuleException("audioTrackIndex " + audioTrackIndex + " não existe neste arquivo");
        }

        if (subtitleTrackIndex == null) {
            return;
        }

        final List<SubtitleTrack> subtitleTracks = mediaInfo.getSubtitles();

        if (subtitleTrackIndex < 0 || subtitleTrackIndex >= subtitleTracks.size()) {
            throw new BusinessRuleException("subtitleTrackIndex " + subtitleTrackIndex + " não existe neste arquivo");
        }
    }

    private Path createWorkDir(UUID sessionId) {
        final String baseDir = settingService.getPlaybackWorkBaseDir();

        if (baseDir == null || baseDir.isBlank()) {
            throw new IntegrationNotConfiguredException(
                    "Diretório de trabalho do playback não configurado. Cadastre o caminho na preferência 'playback.work.base-dir'.");
        }

        final Path workDir = Path.of(baseDir, sessionId.toString());

        try {
            Files.createDirectories(workDir);
        } catch (IOException e) {
            throw new IntegrationException("Falha ao criar o diretório de trabalho da sessão " + sessionId, e);
        }

        return workDir;
    }

    private Path resolveClientPlaylistFile(PlaybackSession session) {
        final Path masterPlaylist = Path.of(session.workDir, "master.m3u8");

        if (Files.isRegularFile(masterPlaylist)) {
            return masterPlaylist;
        }

        return Path.of(session.workDir, "index.m3u8");
    }

    private Path resolveDurationPlaylistFile(PlaybackSession session) {
        final Path variantPlaylist = Path.of(session.workDir, "stream_0.m3u8");

        if (Files.isRegularFile(variantPlaylist)) {
            return variantPlaylist;
        }

        return Path.of(session.workDir, "index.m3u8");
    }

    private void terminateAndCleanup(PlaybackSession session) {
        processTerminationService.terminate(session.ffmpegPid, session.processStartedAt);
        playbackWorkspaceService.delete(Path.of(session.workDir));
    }

}
