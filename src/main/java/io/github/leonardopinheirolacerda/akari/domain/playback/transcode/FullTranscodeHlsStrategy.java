package io.github.leonardopinheirolacerda.akari.domain.playback.transcode;

import io.github.leonardopinheirolacerda.akari.api.dto.MediaInfoResponse;
import io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec.AudioCodec;
import io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec.AudioTranscode;
import io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec.EncodePreset;
import io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec.HlsOptions;
import io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec.HlsPlaylistType;
import io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec.HlsSegmentType;
import io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec.HlsTranscodeSpec;
import io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec.TranscodeMode;
import io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec.VideoCodec;
import io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec.VideoTranscode;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;

/**
 * Estratégia de menor especificidade (fallback): reencoda vídeo (H.264) e áudio (AAC),
 * empacotando em HLS/TS de rendition única. Serve a qualquer profile e pontua baixo, cedendo
 * lugar a estratégias mais específicas quando existirem.
 *
 * <p>Por reencodar tudo sem ladder de resolução, é a opção de maior compatibilidade e maior
 * custo — o oposto de um remux. O tratamento de legenda não é decisão da estratégia: o
 * {@code PlaybackService}/{@code FfmpegClient} acham o tratamento pelo formato (soft WebVTT para
 * SRT, burn-in para os demais).
 */
@ApplicationScoped
public class FullTranscodeHlsStrategy implements HlsTranscodeStrategy {

    private static final int FALLBACK_SCORE = 1;

    @Override
    public Set<Profile> profiles() {
        return Set.of(Profile.WEB, Profile.TV, Profile.MOBILE);
    }

    @Override
    public int score(MediaInfoResponse probe) {
        return FALLBACK_SCORE;
    }

    @Override
    public HlsTranscodeSpec buildSpec(MediaInfoResponse probe) {
        return new HlsTranscodeSpec(
                new VideoTranscode(TranscodeMode.ENCODE, VideoCodec.H264, null, EncodePreset.VERYFAST, null, 2.0, null),
                new AudioTranscode(TranscodeMode.ENCODE, AudioCodec.AAC, 160, null),
                new HlsOptions(4, HlsSegmentType.TS, HlsPlaylistType.EVENT, 0, null)
        );
    }

}
