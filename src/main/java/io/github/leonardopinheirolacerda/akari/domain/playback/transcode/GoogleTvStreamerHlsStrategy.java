package io.github.leonardopinheirolacerda.akari.domain.playback.transcode;

import io.github.leonardopinheirolacerda.akari.api.dto.MediaInfoResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.VideoTrack;
import io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec.*;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Estratégia do profile {@link Profile#GOOGLE_TV_STREAMER}, para o Google TV Streamer (Android
 * TV): H.264 High Profile até o nível 5.2, HEVC Main/Main10 até o nível 5.1 ou AV1 Main até o
 * nível 5.1 — todos até 4K/60fps — limites descritos em
 * https://developers.google.com/cast/docs/media?hl=pt-br (seção "Streamer do Google TV").
 *
 * <p>Prefere remux (copy) quando a fonte já está em H.264, HEVC ou AV1 dentro desses limites,
 * caindo pra encode em HEVC (bom equilíbrio de compressão pra 4K, com encoder de hardware
 * disponível na maioria dos backends — ver {@code HwAccel}) quando não se encaixa. Áudio sempre
 * declarado {@code COPY}-preferido em AAC — o {@code FfmpegClient} confirma contra a faixa de
 * áudio realmente selecionada. Empacota em HLS/fMP4 — HEVC exige fMP4 pro padrão de autoria HLS
 * da Apple; MPEG-TS não é oficialmente suportado pra esse codec.
 */
@ApplicationScoped
public class GoogleTvStreamerHlsStrategy implements HlsTranscodeStrategy {

    /*
    - H.264: "Perfil alto H.264 até o nível 5.2 (4Kx2K/60fps)"
    - HEVC/H.265: "Perfis HEVC / H.265 Main e Main10 até o nível 5.1 (4Kx2K a 60fps)"
    - VP9: "VP9 Profile-2 até 4Kx2K a 60 fps"
    - AV1: "Perfil principal AV1 até o nível 5.1 (4K x 2K a 60 fps)"
    - Áudio: os codecs padrão do Chromecast (FLAC, HE-AAC, LC-AAC, MP3, Opus, Vorbis, WAV/LPCM, WebM) + "Dolby® Atmos™ (sem passagem)"
     */

    private static final int SCORE = 1;
    private static final int MAX_WIDTH = 3840;
    private static final int MAX_HEIGHT = 2160;
    private static final double MAX_FRAME_RATE = 60.1;
    private static final String SQUARE_SAR = "1:1";
    private static final Set<String> COPY_COMPATIBLE_CODECS = Set.of("h264", "hevc", "av1");
    private static final Set<String> COPY_COMPATIBLE_PIX_FMTS = Set.of("yuv420p", "yuv420p10le");

    @Override
    public Set<Profile> profiles() {
        return Set.of(Profile.GOOGLE_TV_STREAMER);
    }

    @Override
    public int score(MediaInfoResponse probe) {
        return SCORE;
    }

    @Override
    public HlsTranscodeSpec buildSpec(MediaInfoResponse probe) {
        return new HlsTranscodeSpec(
                new VideoTranscode(
                        canCopyVideo(probe) ? TranscodeMode.COPY : TranscodeMode.ENCODE,
                        VideoCodec.H265,
                        new RateControl(20, null, 20000, 40000),
                        EncodePreset.VERYFAST,
                        capResolution(probe),
                        2.0,
                        null),
                new AudioTranscode(TranscodeMode.COPY, AudioCodec.AAC, 160, null),
                new HlsOptions(4, HlsSegmentType.FMP4, HlsPlaylistType.EVENT, 0, Set.of(HlsFlag.TEMP_FILE))
        );
    }

    /** {@code Set.of(Resolution.UHD_2160)} se a fonte exceder 4K; vazio (sem downscale) senão. */
    private Set<Resolution> capResolution(MediaInfoResponse probe) {
        final Integer sourceHeight = firstVideoTrack(probe).map(VideoTrack::getHeight).orElse(null);

        return sourceHeight != null && sourceHeight > MAX_HEIGHT
                ? Set.of(Resolution.UHD_2160)
                : Set.of();
    }

    /**
     * {@code true} se a faixa de vídeo já estiver em H.264, HEVC ou AV1, 8 ou 10-bit 4:2:0
     * ({@code yuv420p}/{@code yuv420p10le}), dentro de 3840x2160/60fps e com pixel quadrado — as
     * condições em que um remux chega decodificável no Google TV Streamer sem reencode. SAR
     * ausente é tratado como quadrado (mesma suposição que o {@code FfmpegClient} faz ao não
     * aplicar correção nesse caso).
     */
    private boolean canCopyVideo(MediaInfoResponse probe) {
        final Optional<VideoTrack> track = firstVideoTrack(probe);
        if (track.isEmpty()) {
            return false;
        }

        final VideoTrack video = track.get();
        final String sar = video.getSampleAspectRatio();
        final String codec = video.getCodec();
        final String pixFmt = video.getPixFmt();

        return codec != null && COPY_COMPATIBLE_CODECS.contains(codec.toLowerCase())
                && pixFmt != null && COPY_COMPATIBLE_PIX_FMTS.contains(pixFmt.toLowerCase())
                && (sar == null || SQUARE_SAR.equals(sar))
                && video.getWidth() != null && video.getWidth() <= MAX_WIDTH
                && video.getHeight() != null && video.getHeight() <= MAX_HEIGHT
                && (video.getFrameRate() == null || video.getFrameRate() <= MAX_FRAME_RATE);
    }

    private Optional<VideoTrack> firstVideoTrack(MediaInfoResponse probe) {
        return probe.getVideo()
                .stream()
                .filter(Objects::nonNull)
                .findFirst();
    }

}
