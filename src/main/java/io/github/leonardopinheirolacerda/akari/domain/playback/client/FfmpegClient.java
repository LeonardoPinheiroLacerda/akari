package io.github.leonardopinheirolacerda.akari.domain.playback.client;

import io.github.leonardopinheirolacerda.akari.api.dto.MediaInfoResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.SubtitleTrack;
import io.github.leonardopinheirolacerda.akari.api.dto.VideoTrack;
import io.github.leonardopinheirolacerda.akari.domain.playback.client.model.FfmpegStartParams;
import io.github.leonardopinheirolacerda.akari.domain.playback.client.model.SubtitleMode;
import io.github.leonardopinheirolacerda.akari.domain.playback.client.model.SubtitlePlan;
import io.github.leonardopinheirolacerda.akari.domain.playback.registry.TranscodeProcessRegistry;
import io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec.AudioCodec;
import io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec.AudioTranscode;
import io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec.HlsFlag;
import io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec.HlsOptions;
import io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec.HlsSegmentType;
import io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec.HlsTranscodeSpec;
import io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec.HwAccel;
import io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec.RateControl;
import io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec.Resolution;
import io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec.TranscodeMode;
import io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec.VideoCodec;
import io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec.VideoTranscode;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Monta o comando e sobe o processo {@code ffmpeg} de transcode HLS a partir de uma
 * {@link HlsTranscodeSpec}.
 *
 * <p>{@link #buildCommand} é um pipeline: cada estágio nomeado adiciona seu pedaço do comando,
 * na ordem em que o {@code ffmpeg} espera os argumentos (opções globais → seek → entrada →
 * seleção de faixas/codecs → muxer HLS). Rendition única e ABR divergem a partir da seleção de
 * faixas/codecs, mas convergem de novo no muxer.
 *
 * <p>O resultado do transcode são os próprios arquivos HLS escritos em {@code workDir}, então
 * stdout é descartado; o stderr é capturado pelo {@link TranscodeProcessRegistry} enquanto o
 * processo roda, já que o diretório de trabalho é apagado na limpeza e não haveria como lê-lo
 * depois. Encerrar o processo não é responsabilidade desta classe — é gerência de processo
 * genérica, sem nada de ffmpeg/HLS, que fica no {@code ProcessTerminationService}.
 *
 * <p>Não traduz falhas em {@code AkariException} — {@link #start} lança {@link FfmpegException},
 * que quem chama (o {@code PlaybackService}) embrulha em {@code IntegrationException}.
 */
@ApplicationScoped
public class FfmpegClient {

    /** Codecs de legenda de texto (SubRip): entregues como WebVTT soft. */
    private static final Set<String> SRT_CODECS = Set.of("subrip", "srt");

    /** Codecs de legenda de imagem (bitmap): queimados via {@code overlay}, não via libass. */
    private static final Set<String> IMAGE_SUBTITLE_CODECS = Set.of("hdmv_pgs_subtitle", "dvd_subtitle", "dvb_subtitle");

    private static final String MASTER_NAME = "master.m3u8";

    /** Frame rate usado pro cálculo de GOP quando o probe não traz um valor (não deveria acontecer). */
    private static final double DEFAULT_FRAME_RATE = 24.0;

    @ConfigProperty(name = "akari.playback.ffmpeg.binary", defaultValue = "ffmpeg")
    String ffmpegBinary;

    @Inject
    TranscodeProcessRegistry transcodeProcessRegistry;

    /**
     * Sobe um processo {@code ffmpeg} de transcode HLS com os parâmetros informados.
     *
     * <p>Registra o início no log (sessão, arquivo, pid, faixas escolhidas e o comando completo)
     * e passa o processo pro {@link TranscodeProcessRegistry}, que acompanha o resto do ciclo de
     * vida dele (stderr, encerramento esperado ou não).
     *
     * @param params spec, entrada, destino e seleção de faixas/aceleração do transcode
     * @return o pid do processo iniciado
     * @throws FfmpegException se o binário não puder ser iniciado (ausente, sem permissão)
     */
    public Long start(FfmpegStartParams params) {
        final List<String> command = buildCommand(params);
        final String subtitleCodec = subtitleCodec(params.mediaInfo(), params.subtitleTrackIndex());

        final ProcessBuilder processBuilder = new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD);

        final Process process;

        try {
            process = processBuilder.start();
        } catch (IOException e) {
            throw new FfmpegException(
                    "Não foi possível iniciar o ffmpeg (binário '" + ffmpegBinary + "' ausente ou sem permissão de execução)",
                    e
            );
        }

        Log.infof(
                "ffmpeg iniciado pra sessão %s (arquivo %d, pid %d): audioTrackIndex=%s, subtitleTrackIndex=%s (codec=%s), "
                        + "vídeo de entrada %dx%d (DAR=%s). Comando: %s",
                params.sessionId(),
                params.fileId(),
                process.pid(),
                params.audioTrackIndex(),
                params.subtitleTrackIndex(),
                subtitleCodec,
                videoWidth(params.mediaInfo()),
                videoHeight(params.mediaInfo()),
                displayAspectRatio(params.mediaInfo()),
                String.join(" ", command));

        transcodeProcessRegistry.track(params.sessionId(), process);

        return process.pid();
    }

    /**
     * Monta os argumentos do comando {@code ffmpeg} pro transcode HLS, sem efeito colateral —
     * separado de {@link #start} pra ser testável isoladamente.
     *
     * @param params spec, entrada, destino e seleção de faixas/aceleração do transcode
     * @return os argumentos completos, incluindo o binário na posição 0
     */
    List<String> buildCommand(FfmpegStartParams params) {
        final List<String> args = new ArrayList<>();
        final double startOffsetSeconds = startOffsetSeconds(params);

        appendGlobalOptions(args);
        appendSeek(args, startOffsetSeconds);
        appendInput(args, params.inputFile());
        appendEncoding(args, params, startOffsetSeconds);

        return args;
    }

    private double startOffsetSeconds(FfmpegStartParams params) {
        return params.startOffsetSeconds() != null ? params.startOffsetSeconds() : 0.0;
    }

    private void appendGlobalOptions(List<String> args) {
        args.add(ffmpegBinary);
        args.add("-y");
        args.add("-hide_banner");
        args.add("-loglevel");
        args.add("warning");
        args.add("-nostats");
    }

    private void appendSeek(List<String> args, double startOffsetSeconds) {
        if (startOffsetSeconds <= 0) {
            return;
        }
        args.add("-ss");
        args.add(formatSeconds(startOffsetSeconds));
    }

    private void appendInput(List<String> args, Path inputFile) {
        args.add("-i");
        args.add(inputFile.toString());
    }

    /**
     * Decide a ladder de resolução, o GOP e o plano de legenda uma única vez, e despacha pra
     * rendition única ou ABR — os dois caminhos reaproveitam exatamente a mesma legenda.
     */
    private void appendEncoding(List<String> args, FfmpegStartParams params, double startOffsetSeconds) {
        final HlsTranscodeSpec spec = params.spec();
        final List<Resolution> ladder = ladder(spec.video());
        final Integer keyframeFrames = resolveKeyframeFrames(spec.video(), spec.hls(), params.mediaInfo());
        final SubtitlePlan subtitlePlan = resolveSubtitlePlan(params, startOffsetSeconds);

        warnIfAudioContainerMismatch(spec.audio(), spec.hls());
        warnIfFlacBitrateIgnored(spec.audio());

        if (ladder.size() > 1) {
            appendAbr(args, spec, ladder, params, keyframeFrames, subtitlePlan);
        } else {
            appendSingle(args, spec, ladder, params, keyframeFrames, subtitlePlan);
        }
    }

    /**
     * {@link AudioCodec#OPUS} e {@link AudioCodec#FLAC} não têm tipo de stream oficial no
     * MPEG-TS — o ffmpeg ainda produz o segmento (testado localmente), mas com o próprio aviso
     * de que o stream "é muxado como dado privado e pode não ser reconhecido na leitura". Como
     * o HLS padrão exige {@link HlsSegmentType#FMP4} pra esses codecs, só logamos o aviso aqui
     * em vez de bloquear — o comando continua sendo gerado normalmente.
     */
    private void warnIfAudioContainerMismatch(AudioTranscode audio, HlsOptions hls) {
        if (audio == null || audio.mode() != TranscodeMode.ENCODE) {
            return;
        }

        final boolean needsFmp4 = audio.codec() == AudioCodec.OPUS || audio.codec() == AudioCodec.FLAC;
        final boolean isTs = hls.segmentType() != HlsSegmentType.FMP4;

        if (needsFmp4 && isTs) {
            Log.warnf(
                    "Áudio %s em segmento MPEG-TS: fora do padrão HLS (precisa de fMP4) e pode não ser reconhecido por alguns players",
                    audio.codec());
        }
    }

    /**
     * {@link AudioCodec#FLAC} é lossless — {@code bitrateKbps} da spec não se aplica. Resolvido
     * uma vez aqui (em vez de dentro de {@link #appendAudioBitrate}) pra não repetir o mesmo
     * aviso uma vez por rendition no ABR.
     */
    private void warnIfFlacBitrateIgnored(AudioTranscode audio) {
        if (audio != null && audio.codec() == AudioCodec.FLAC && audio.bitrateKbps() != null) {
            Log.warnf("bitrateKbps=%d ignorado: FLAC é lossless, sem bitrate-alvo", audio.bitrateKbps());
        }
    }

    /**
     * Converte {@link VideoTranscode#gopSeconds()} num número de frames, ajustando o GOP (não o
     * segmento) pra caber um número inteiro de vezes em {@link HlsOptions#segmentSeconds()} —
     * o segmento é o valor que o player/CDN consome, então fica exatamente como configurado; o
     * GOP é quem se recalcula ao redor dele. Como o resultado precisa ser um número inteiro de
     * frames, o GOP real pode diferir em frações de segundo do valor pedido — inofensivo, já que
     * o muxer HLS corta no próximo keyframe na ou após o alvo, nunca exatamente nele.
     *
     * @return o valor de {@code -g}/{@code -keyint_min}; {@code null} se nenhum GOP deve ser
     *         forçado ({@code gopSeconds} não especificado, ou {@code video.mode()} é
     *         {@code COPY}, onde não há encoder pra obedecer a flag)
     */
    private Integer resolveKeyframeFrames(VideoTranscode video, HlsOptions hls, MediaInfoResponse mediaInfo) {
        if (video == null || video.mode() != TranscodeMode.ENCODE || video.gopSeconds() == null) {
            return null;
        }

        final double desiredSegmentSeconds = hls.segmentSeconds();
        final int gopsPerSegment = Math.max(1, (int) Math.round(desiredSegmentSeconds / video.gopSeconds()));

        final double actualGopSeconds = desiredSegmentSeconds / gopsPerSegment;
        final double frameRate = frameRate(mediaInfo);

        return Math.max(1, (int) Math.round(actualGopSeconds * frameRate));
    }

    private double frameRate(MediaInfoResponse mediaInfo) {
        return mediaInfo.getVideo()
                .stream()
                .map(VideoTrack::getFrameRate)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(DEFAULT_FRAME_RATE);
    }

    /**
     * Largura/altura reais do vídeo, pra escalar a legenda de imagem (autorada no canvas próprio
     * dela, ex.: 1920x1080 em BDs, que pode não bater com a resolução real do vídeo, ex.:
     * 1440x1080 em conteúdo 4:3) pro tamanho do frame antes do {@link #overlayFilter overlay} —
     * sem isso, a legenda cola fora de posição sempre que os dois canvas têm tamanhos diferentes.
     * Probe sem largura/altura não deveria acontecer (todo stream de vídeo tem as duas), por isso
     * lança em vez de arriscar uma legenda mal posicionada com um valor chutado.
     */
    private int videoWidth(MediaInfoResponse mediaInfo) {
        return mediaInfo.getVideo()
                .stream()
                .map(VideoTrack::getWidth)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new FfmpegException("Arquivo sem largura de vídeo no probe — não é possível escalar a legenda de imagem"));
    }

    private int videoHeight(MediaInfoResponse mediaInfo) {
        return mediaInfo.getVideo()
                .stream()
                .map(VideoTrack::getHeight)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new FfmpegException("Arquivo sem altura de vídeo no probe — não é possível escalar a legenda de imagem"));
    }

    private String displayAspectRatio(MediaInfoResponse mediaInfo) {
        return mediaInfo.getVideo()
                .stream()
                .map(VideoTrack::getDisplayAspectRatio)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /** Parseia {@code "W:H"} (SAR/DAR do probe) num número; ausente ou malformado → {@code null}. */
    private Double parseRatio(String ratio) {
        if (ratio == null) {
            return null;
        }

        final String[] parts = ratio.split(":");

        if (parts.length != 2) {
            return null;
        }

        try {
            final double numerator = Double.parseDouble(parts[0]);
            final double denominator = Double.parseDouble(parts[1]);

            if (denominator <= 0) {
                return null;
            }

            return numerator / denominator;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Largura final do vídeo pra uma altura-alvo, corrigida pelo DAR real — o mesmo resultado que
     * {@link #sarCorrectedScale} produz via {@code iw*sar}, mas calculado em Java porque a legenda
     * de imagem precisa de um número concreto pra escalar o bitmap antes do {@link #overlayFilter
     * overlay} (não dá pra referenciar a saída de outro pad do filtergraph por nome sem o filtro
     * {@code scale2ref}, mais obscuro). Sem DAR no probe, cai pra proporção bruta
     * largura/altura do vídeo — equivale a assumir pixel quadrado, sem correção.
     *
     * @param targetHeight altura-alvo (rung do ABR ou profile); {@code null} mantém a altura
     *                     original do vídeo
     */
    private int targetVideoWidth(MediaInfoResponse mediaInfo, Integer targetHeight) {
        final int rawWidth = videoWidth(mediaInfo);
        final int rawHeight = videoHeight(mediaInfo);
        final int height = targetHeight != null
                ? targetHeight
                : rawHeight;

        final Double dar = parseRatio(displayAspectRatio(mediaInfo));
        final double ratio = dar != null
                ? dar
                : ((double) rawWidth / rawHeight);

        return evenFloor((int) Math.round(height * ratio));
    }

    /** Arredonda pra baixo até o próximo par — exigido pelos encoders, mesma regra do {@code trunc(x/2)*2} do ffmpeg. */
    private int evenFloor(int value) {
        return (value / 2) * 2;
    }

    /**
     * Corrige a SAR pra 1:1 (pixel quadrado) escalando a largura pela proporção real decodificada
     * ({@code iw*sar}) e fixando {@code setsar=1} — sem isso, vídeo anamórfico (pixel não
     * quadrado, ex.: BD 4:3 que é 16:9 na exibição) sai com a razão de aspecto errada, porque o
     * player assume pixel quadrado e ignora a SAR do stream. Se {@code targetHeight} for
     * informado, encadeia o downscale pra altura da rendition logo em seguida — nessa ordem,
     * porque o downscale precisa partir da geometria já corrigida, e quem queima legenda em cima
     * (texto ou imagem) precisa fazer isso depois deste filtro, nunca antes.
     *
     * <p>Sem efeito em vídeo com pixel quadrado (SAR 1:1 ou ausente — {@code iw*sar} dá a própria
     * largura): o BLEACH (1440x1080, DAR 4:3, SAR 1:1) continua saindo 1440x1080, sem esticar.
     */
    private String sarCorrectedScale(Integer targetHeight) {
        final String squarePixels = "scale=trunc(iw*sar/2)*2:ih,setsar=1";

        if (targetHeight == null) {
            return squarePixels;
        }

        return squarePixels + ",scale=-2:" + targetHeight;
    }

    /**
     * Descobre o codec da faixa de legenda escolhida no probe (índice entre as legendas). Índice
     * ausente ou fora de faixa → {@code null} (sem legenda).
     */
    private String subtitleCodec(MediaInfoResponse mediaInfo, Integer subtitleTrackIndex) {
        if (subtitleTrackIndex == null) {
            return null;
        }

        final List<SubtitleTrack> subtitles = mediaInfo.getSubtitles();

        if (subtitleTrackIndex < 0 || subtitleTrackIndex >= subtitles.size()) {
            return null;
        }

        return subtitles.get(subtitleTrackIndex).getCodec();
    }

    // ---------- rendition única -------------------------------------------

    /**
     * Rendition única: mapeia as faixas (com a legenda já resolvida), codifica vídeo/áudio,
     * finaliza o grupo de legenda soft (se houver) e fecha no muxer HLS.
     */
    private void appendSingle(
            List<String> args,
            HlsTranscodeSpec spec,
            List<Resolution> ladder,
            FfmpegStartParams params,
            Integer keyframeFrames,
            SubtitlePlan subtitlePlan) {
        final Resolution resolution = ladder.isEmpty()
                ? null
                : ladder.get(0);

        final String videoMapTarget = appendVideoFilterGraph(args, params.mediaInfo(), subtitlePlan, resolution, "v0");

        appendStreamMapping(args, videoMapTarget, params.audioTrackIndex(), subtitlePlan);
        appendVideo(args, spec.video(), resolution, subtitlePlan, params.hwAccel(), keyframeFrames);
        appendAudio(args, spec.audio(), params.mediaInfo(), params.audioTrackIndex());
        appendSubtitleGroup(args, subtitlePlan);
        appendMuxer(args, spec.hls(), params.workDir(), subtitlePlan.isSoft());
    }

    /**
     * Resolve como a legenda escolhida (se houver) entra no transcode, pelo codec da faixa: SRT
     * (texto) → {@link SubtitleMode#SOFT}; demais formatos de texto (ex.: ASS) →
     * {@link SubtitleMode#BURN_TEXT}; formatos de imagem (PGS, VobSub, DVB) →
     * {@link SubtitleMode#BURN_IMAGE}.
     */
    private SubtitlePlan resolveSubtitlePlan(FfmpegStartParams params, double startOffsetSeconds) {
        final Integer subtitleTrackIndex = params.subtitleTrackIndex();
        final String subtitleCodec = subtitleCodec(params.mediaInfo(), subtitleTrackIndex);

        if (subtitleTrackIndex == null || subtitleCodec == null) {
            return new SubtitlePlan(SubtitleMode.NONE, null, null);
        }

        final String normalizedCodec = subtitleCodec.toLowerCase(Locale.ROOT);

        if (SRT_CODECS.contains(normalizedCodec)) {
            return new SubtitlePlan(SubtitleMode.SOFT, subtitleTrackIndex, null);
        }

        if (IMAGE_SUBTITLE_CODECS.contains(normalizedCodec)) {
            return new SubtitlePlan(SubtitleMode.BURN_IMAGE, subtitleTrackIndex, null);
        }

        return new SubtitlePlan(
                SubtitleMode.BURN_TEXT,
                subtitleTrackIndex,
                burnSubtitleFilter(params.inputFile(), subtitleTrackIndex, startOffsetSeconds));
    }

    /**
     * Filtro de hardsub de texto. O {@code subtitles} lê a legenda direto do arquivo, com os
     * tempos originais, mas o {@code -ss} de entrada (seek com reinício) zera o PTS do vídeo —
     * sem compensar, ele desenharia as falas do início do episódio. Desloca o PTS pro tempo
     * original antes do burn e volta a zerar depois, mantendo o timeline da playlist começando
     * em 0.
     */
    private String burnSubtitleFilter(Path inputFile, int subtitleTrackIndex, double startOffsetSeconds) {
        final String escapedPath = escapeFilterPath(inputFile.toString());
        final String subtitles = "subtitles=" + escapedPath + ":si=" + subtitleTrackIndex;

        if (startOffsetSeconds <= 0) {
            return subtitles;
        }

        return "setpts=PTS+" + formatSeconds(startOffsetSeconds) + "/TB," + subtitles + ",setpts=PTS-STARTPTS";
    }

    /**
     * Escapa caracteres que o parser do filtergraph do ffmpeg trata como sintaxe (`:` separa
     * opções do filtro, `[`/`]` denotam link de pad, `'` fecha aspas) — sem isso, um caminho com
     * colchete (comum em nome de release de fansub, ex.: {@code [Erai-raws] ... [1080p][Hash].mkv})
     * quebra o parse do filtro e o ffmpeg morre antes de processar qualquer frame. A barra
     * invertida é escapada primeiro, pra não duplicar as que esta função mesma insere.
     */
    private String escapeFilterPath(String path) {
        return path
                .replace("\\", "\\\\")
                .replace(":", "\\:")
                .replace("'", "\\'")
                .replace("[", "\\[")
                .replace("]", "\\]");
    }

    /**
     * Overlay pra legenda de imagem (PGS, VobSub, DVB): decodifica o stream de legenda como vídeo
     * e sobrepõe no stream principal — diferente do {@code subtitles} (libass), que só lê legenda
     * de texto. Os dois streams vêm do mesmo demuxer com o mesmo {@code -ss} de entrada, então os
     * PTS já saem alinhados, sem precisar do deslocamento que {@link #burnSubtitleFilter} faz pro
     * caso de texto.
     *
     * <p>O vídeo é corrigido de SAR (e, se houver rung, já reduzido pra altura dela via
     * {@link #sarCorrectedScale}) antes do overlay — a legenda precisa ser queimada na geometria
     * final, não esticada depois (senão o texto sai distorcido pelo redimensionamento). A legenda
     * de imagem é desenhada no canvas próprio dela (ex.: 1920x1080 em BDs), que pode não bater com
     * a resolução real do vídeo — por isso ela também é escalada pra essa mesma geometria final
     * ({@link #targetVideoWidth}) antes de sobrepor; sem isso, a legenda sai fora de posição
     * (deslocada, não centralizada) sempre que os dois canvas divergem.
     *
     * @param mediaInfo probe da mídia, pra saber a resolução/SAR real do vídeo
     * @param subtitleTrackIndex posição da legenda entre as faixas de legenda do arquivo
     * @param resolution resolução da rendition, pra embutir o downscale final no mesmo filtro;
     *                   {@code null} mantém a altura original do vídeo
     * @param outputLabel nome do pad de saída do filtro, referenciado depois no {@code -map}
     */
    private String overlayFilter(MediaInfoResponse mediaInfo, Integer subtitleTrackIndex, Resolution resolution, String outputLabel) {
        final Integer targetHeight = resolution != null ? resolution.height : null;
        final int finalHeight = targetHeight != null ? targetHeight : videoHeight(mediaInfo);
        final int finalWidth = targetVideoWidth(mediaInfo, targetHeight);

        final String videoLabel = "vcorr_" + outputLabel;
        final String subtitleLabel = "sub_" + outputLabel;

        final String videoChain = "[0:v]" + sarCorrectedScale(targetHeight) + "[" + videoLabel + "]";
        final String subtitleChain = "[0:s:" + subtitleTrackIndex + "]scale=" + finalWidth + ":" + finalHeight + "[" + subtitleLabel + "]";
        final String overlayChain = "[" + videoLabel + "][" + subtitleLabel + "]overlay[" + outputLabel + "]";

        return videoChain + ";" + subtitleChain + ";" + overlayChain;
    }

    /** Uma cadeia {@link #overlayFilter} por rendition do ABR, com os pads {@code v0}, {@code v1}... */
    private String overlayFilterComplexForLadder(MediaInfoResponse mediaInfo, Integer subtitleTrackIndex, List<Resolution> ladder) {
        final StringJoiner chains = new StringJoiner(";");

        for (int i = 0; i < ladder.size(); i++) {
            chains.add(overlayFilter(mediaInfo, subtitleTrackIndex, ladder.get(i), "v" + i));
        }

        return chains.toString();
    }

    /**
     * Pra legenda de imagem, adiciona o {@code -filter_complex} do overlay (com o downscale da
     * rendition já embutido) e devolve o label do pad de vídeo resultante, pra mapear no lugar de
     * {@code 0:v:0}. Nos demais modos não adiciona nada e devolve {@code 0:v:0} mesmo.
     */
    private String appendVideoFilterGraph(List<String> args,
                                          MediaInfoResponse mediaInfo,
                                          SubtitlePlan subtitlePlan,
                                          Resolution resolution,
                                          String label) {

        if (!subtitlePlan.isBurnImage()) {
            return "0:v:0";
        }

        args.add("-filter_complex");
        args.add(overlayFilter(mediaInfo, subtitlePlan.trackIndex(), resolution, label));

        return "[" + label + "]";
    }

    private void appendStreamMapping(List<String> args, String videoMapTarget, Integer audioTrackIndex, SubtitlePlan subtitlePlan) {
        args.add("-map");
        args.add(videoMapTarget);
        args.add("-map");
        args.add("0:a:" + audioTrackIndex);

        if (subtitlePlan.isSoft()) {
            args.add("-map");
            args.add("0:s:" + subtitlePlan.trackIndex());
        }
    }

    /**
     * {@code TranscodeMode#COPY} cede pra encode (usando os campos de encode da própria
     * {@code video}, preenchidos pela estratégia mesmo quando o modo preferido é {@code COPY})
     * quando a legenda escolhida exige burn-in — stream copy não decodifica, então não há como
     * aplicar o filtro de overlay/queima nesse caso.
     */
    private void appendVideo(List<String> args,
                             VideoTranscode video,
                             Resolution resolution,
                             SubtitlePlan subtitlePlan,
                             HwAccel hwAccel,
                             Integer keyframeFrames) {

        final boolean needsBurn = subtitlePlan.mode() == SubtitleMode.BURN_TEXT || subtitlePlan.isBurnImage();

        if (video == null || (video.mode() == TranscodeMode.COPY && !needsBurn)) {
            args.add("-c:v");
            args.add("copy");
            return;
        }

        args.add("-c:v");
        args.add(encoder(video.codec(), hwAccel));

        appendPresetAndTune(args, video);
        appendRateControl(args, video.rateControl(), null);
        appendGop(args, keyframeFrames, null);

        if (subtitlePlan.isBurnImage()) {
            return;
        }

        args.add("-vf");
        args.add(videoFilter(resolution, subtitlePlan.burnFilter()));
    }

    /** {@code -preset}/{@code -tune}: compartilhado entre rendition única e cada variante do ABR. */
    private void appendPresetAndTune(List<String> args, VideoTranscode video) {
        if (video.preset() != null) {
            args.add("-preset");
            args.add(video.preset().name().toLowerCase(Locale.ROOT));
        }
        if (video.tune() != null) {
            args.add("-tune");
            args.add(video.tune().name().toLowerCase(Locale.ROOT));
        }
    }

    /**
     * Cadeia de filtros de vídeo: correção de SAR ({@link #sarCorrectedScale}, com o downscale
     * pra resolução da rung já embutido, se houver) e, por cima, o burn de legenda de texto —
     * nessa ordem, pra queimar já na geometria final. Sempre devolve pelo menos a correção de
     * SAR: mesmo sem downscale nem legenda, a saída do transcode deve sair com pixel quadrado.
     */
    private String videoFilter(Resolution resolution, String burnFilter) {
        final List<String> filters = new ArrayList<>();

        filters.add(sarCorrectedScale(resolution != null ? resolution.height : null));

        if (burnFilter != null) {
            filters.add(burnFilter);
        }

        return String.join(",", filters);
    }

    /**
     * {@code TranscodeMode#COPY} cede pra encode (usando os campos de encode da própria
     * {@code audio}, preenchidos pela estratégia mesmo quando o modo preferido é {@code COPY})
     * se a faixa de áudio realmente selecionada ({@code audioTrackIndex}) não tiver o codec
     * esperado — a estratégia decide {@code COPY} ao avaliar o probe inteiro, sem saber qual
     * faixa de áudio o cliente vai selecionar, então a validação final (contra a faixa de fato
     * escolhida) só pode ser feita aqui.
     */
    private void appendAudio(List<String> args, AudioTranscode audio, MediaInfoResponse mediaInfo, Integer audioTrackIndex) {
        final boolean copy = audio == null
                || (audio.mode() == TranscodeMode.COPY && matchesSelectedAudio(audio.codec(), mediaInfo, audioTrackIndex));

        if (copy) {
            args.add("-c:a");
            args.add("copy");
            return;
        }

        args.add("-c:a");
        args.add(audioEncoder(audio.codec()));
        appendAudioBitrate(args, audio, null);
        appendAudioChannels(args, audio, null);
    }

    /** {@code true} se a faixa de áudio selecionada já estiver codificada no {@code codec} alvo. */
    private boolean matchesSelectedAudio(AudioCodec codec, MediaInfoResponse mediaInfo, Integer audioTrackIndex) {
        final String probedCodec = mediaInfo.getAudio().get(audioTrackIndex).getCodec();

        return switch (codec) {
            case AAC -> "aac".equalsIgnoreCase(probedCodec);
            case FLAC -> "flac".equalsIgnoreCase(probedCodec);
            case OPUS -> "opus".equalsIgnoreCase(probedCodec);
        };
    }

    /**
     * {@code -b:a}, exceto pra {@link AudioCodec#FLAC} (lossless, sem bitrate-alvo — já avisado
     * por {@link #warnIfFlacBitrateIgnored}). Qualificado por {@code :N} no ABR (uma rendition),
     * sem qualificador na rendition única — mesmo padrão de {@link #appendRateControl}.
     */
    private void appendAudioBitrate(List<String> args, AudioTranscode audio, Integer variantIndex) {
        if (audio.bitrateKbps() == null || audio.codec() == AudioCodec.FLAC) {
            return;
        }

        args.add(variantIndex != null ? "-b:a:" + variantIndex : "-b:a");
        args.add(audio.bitrateKbps() + "k");
    }

    /** {@code -ac}, qualificado por {@code :a:N} no ABR (uma rendition). */
    private void appendAudioChannels(List<String> args, AudioTranscode audio, Integer variantIndex) {
        if (audio.channels() == null) {
            return;
        }

        args.add(variantIndex != null ? "-ac:a:" + variantIndex : "-ac");
        args.add(String.valueOf(audio.channels()));
    }

    /** Nome do encoder ffmpeg pro codec de áudio. */
    private String audioEncoder(AudioCodec codec) {
        return switch (codec) {
            case AAC -> "aac";
            case FLAC -> "flac";
            case OPUS -> "libopus";
        };
    }

    /**
     * Só quando a legenda é soft, na rendition única: codec WebVTT e agrupamento da (única)
     * variante no master playlist. No ABR o agrupamento já sai de {@link #appendAbrStreamMapping}
     * — só o codec (via {@link #appendSubtitleCodec}) é reaproveitado.
     */
    private void appendSubtitleGroup(List<String> args, SubtitlePlan subtitlePlan) {
        if (!subtitlePlan.isSoft()) {
            return;
        }

        appendSubtitleCodec(args, subtitlePlan);
        args.add("-var_stream_map");
        args.add("v:0,a:0,s:0,sgroup:subs");
        args.add("-master_pl_name");
        args.add(MASTER_NAME);
    }

    private void appendSubtitleCodec(List<String> args, SubtitlePlan subtitlePlan) {
        if (!subtitlePlan.isSoft()) {
            return;
        }

        args.add("-c:s");
        args.add("webvtt");
    }

    // ---------- ABR (múltiplas resoluções) --------------------------------

    /**
     * ABR: mapeia as faixas de cada rendition (mais a legenda, se houver), codifica vídeo/áudio
     * por rendition, finaliza o agrupamento das variantes no master playlist e fecha no muxer
     * HLS. Hardsub em ABR funciona (o filtro é aplicado em cada rendition), mas multiplica o
     * custo de encode por uma legenda só — daí o aviso.
     */
    private void appendAbr(List<String> args,
                           HlsTranscodeSpec spec,
                           List<Resolution> ladder,
                           FfmpegStartParams params,
                           Integer keyframeFrames,
                           SubtitlePlan subtitlePlan) {

        if (subtitlePlan.mode() == SubtitleMode.BURN_TEXT) {
            Log.warnf(
                    "Hardsub em ABR: o filtro de burn vai rodar em cada uma das %d renditions, não é o ideal mas é suportado",
                    ladder.size());
        }

        if (subtitlePlan.isBurnImage()) {
            args.add("-filter_complex");
            args.add(overlayFilterComplexForLadder(params.mediaInfo(), subtitlePlan.trackIndex(), ladder));
        }

        final String varStreamMap = appendAbrStreamMapping(args, ladder, params.audioTrackIndex(), subtitlePlan);
        appendAbrVideoCodecs(args, ladder, spec.video(), params.hwAccel(), subtitlePlan, keyframeFrames);
        appendAbrAudioCodecs(args, ladder, spec.audio());
        appendSubtitleCodec(args, subtitlePlan);

        args.add("-var_stream_map");
        args.add(varStreamMap);
        args.add("-master_pl_name");
        args.add(MASTER_NAME);

        appendMuxer(args, spec.hls(), params.workDir(), true);
    }

    private String appendAbrStreamMapping(List<String> args,
                                          List<Resolution> ladder,
                                          Integer audioTrackIndex,
                                          SubtitlePlan subtitlePlan) {
        final StringJoiner varStreamMap = new StringJoiner(" ");

        for (int i = 0; i < ladder.size(); i++) {
            args.add("-map");
            args.add(subtitlePlan.isBurnImage()
                    ? "[v" + i + "]"
                    : "0:v:0"
            );
            args.add("-map");
            args.add("0:a:" + audioTrackIndex);

            final String variant = "v:" + i + ",a:" + i;
            varStreamMap.add(subtitlePlan.isSoft()
                    ? variant + ",s:0,sgroup:subs"
                    : variant
            );
        }

        if (subtitlePlan.isSoft()) {
            args.add("-map");
            args.add("0:s:" + subtitlePlan.trackIndex());
        }

        return varStreamMap.toString();
    }

    /**
     * A resolução de cada rendition é imposta via {@code -filter:v:i} (não {@code -s:v:i}, que
     * forçaria a largura fixa da rung — errada pra fonte que não é 16:9, ex.: 4:3) — reaproveita o
     * mesmo {@link #videoFilter} da rendition única, que corrige a SAR e reduz pra altura da rung
     * preservando a proporção real do vídeo. Pulado pra legenda de imagem, cujo
     * {@code -filter_complex} ({@link #overlayFilterComplexForLadder}) já faz a correção de SAR e
     * o downscale.
     */
    private void appendAbrVideoCodecs(List<String> args,
                                      List<Resolution> ladder,
                                      VideoTranscode video,
                                      HwAccel hwAccel,
                                      SubtitlePlan subtitlePlan,
                                      Integer keyframeFrames) {

        final String encoder = encoder(video.codec(), hwAccel);

        for (int i = 0; i < ladder.size(); i++) {
            args.add("-c:v:" + i);
            args.add(encoder);

            appendPresetAndTune(args, video);
            appendRateControl(args, video.rateControl(), i);
            appendGop(args, keyframeFrames, i);

            if (!subtitlePlan.isBurnImage()) {
                final String burnFilter = subtitlePlan.mode() == SubtitleMode.BURN_TEXT
                        ? subtitlePlan.burnFilter()
                        : null;

                args.add("-filter:v:" + i);
                args.add(videoFilter(ladder.get(i), burnFilter));
            }
        }
    }

    private void appendAbrAudioCodecs(List<String> args, List<Resolution> ladder, AudioTranscode audio) {
        final boolean encode = audio != null && audio.mode() == TranscodeMode.ENCODE;
        final String codec = encode ? audioEncoder(audio.codec()) : "copy";

        for (int i = 0; i < ladder.size(); i++) {
            args.add("-c:a:" + i);
            args.add(codec);

            if (encode) {
                appendAudioBitrate(args, audio, i);
                appendAudioChannels(args, audio, i);
            }
        }
    }

    // ---------- comuns -----------------------------------------------------

    /** Opções do muxer HLS e o path de saída — a playlist devolvida pro player. */
    private void appendMuxer(List<String> args, HlsOptions hls, Path workDir, boolean variant) {

        args.add("-f");
        args.add("hls");
        args.add("-hls_time");
        args.add(String.valueOf(hls.segmentSeconds()));

        if (hls.playlistType() != null) {
            args.add("-hls_playlist_type");

            final String playlistType = hls.playlistType()
                    .name()
                    .toLowerCase(Locale.ROOT);

            args.add(playlistType);
        }

        final boolean fmp4 = hls.segmentType() == HlsSegmentType.FMP4;

        if (hls.segmentType() != null) {
            args.add("-hls_segment_type");
            args.add(fmp4 ? "fmp4" : "mpegts");
        }

        args.add("-hls_list_size");
        args.add(String.valueOf(hls.listSize()));

        if (hls.flags() != null && !hls.flags().isEmpty()) {
            args.add("-hls_flags");
            final String joinedFlags = joinFlags(hls.flags());
            args.add(joinedFlags);
        }

        final String ext = fmp4 ? "m4s" : "ts";
        final String segPattern = variant
                ? "segment_%v_%d." + ext
                : "segment_%d." + ext;

        args.add("-hls_segment_filename");

        args.add(workDir.resolve(segPattern).toString());

        args.add(workDir.resolve(variant
                ? "stream_%v.m3u8"
                : "index.m3u8").toString());
    }

    /**
     * {@code -g}/{@code -keyint_min}/{@code -sc_threshold}: GOP fixo e fechado, sem scene-cut.
     * Qualificado por {@code :v} (rendition única, {@code variantIndex == null}) ou
     * {@code :v:N} (ABR, uma rendition) — mesmo padrão de {@link #appendRateControl}, testado
     * localmente que cada forma aplica corretamente ao(s) stream(s) de vídeo certos.
     */
    private void appendGop(List<String> args, Integer keyframeFrames, Integer variantIndex) {
        if (keyframeFrames == null) {
            return;
        }

        final String spec = variantIndex != null ? ":v:" + variantIndex : ":v";

        args.add("-g" + spec);
        args.add(String.valueOf(keyframeFrames));
        args.add("-keyint_min" + spec);
        args.add(String.valueOf(keyframeFrames));
        args.add("-sc_threshold" + spec);
        args.add("0");
    }

    /**
     * {@code -crf}/{@code -b:v}/{@code -maxrate}/{@code -bufsize}, qualificados por
     * {@code :v} (rendition única, {@code variantIndex == null}) ou {@code :v:N} (ABR, uma
     * rendition) — testado localmente que {@code :v} sem índice se comporta igual à flag crua
     * quando há só um stream de vídeo, então não muda nada pra rendition única.
     */
    private void appendRateControl(List<String> args, RateControl rc, Integer variantIndex) {
        if (rc == null) {
            return;
        }

        final String spec = variantIndex != null ? ":v:" + variantIndex : ":v";

        if (rc.crf() != null) {
            args.add("-crf" + spec);
            args.add(String.valueOf(rc.crf()));
        }
        if (rc.bitrateKbps() != null) {
            args.add("-b" + spec);
            args.add(rc.bitrateKbps() + "k");
        }
        if (rc.maxrateKbps() != null) {
            args.add("-maxrate" + spec);
            args.add(rc.maxrateKbps() + "k");
        }
        if (rc.bufsizeKb() != null) {
            args.add("-bufsize" + spec);
            args.add(rc.bufsizeKb() + "k");
        }
    }

    /** Ladder ordenada da maior para a menor resolução; vazia se {@code COPY}/sem resoluções. */
    private List<Resolution> ladder(VideoTranscode video) {
        if (video == null || video.resolutions() == null) {
            return List.of();
        }

        return video.resolutions()
                .stream()
                .sorted(Comparator.comparingInt((Resolution resolution) -> resolution.height).reversed())
                .toList();
    }

    /**
     * Nome do encoder ffmpeg para o par codec + aceleração. Cada backend de hardware usa seus
     * próprios encoders; sem aceleração (ou quando o backend não tem encoder para o codec, como
     * AV1 no VideoToolbox) cai no encoder de software.
     */
    private String encoder(VideoCodec codec, HwAccel hwAccel) {
        return switch (hwAccel) {
            case NONE -> softwareEncoder(codec);
            case VIDEOTOOLBOX -> switch (codec) {
                case H264 -> "h264_videotoolbox";
                case H265 -> "hevc_videotoolbox";
                case AV1 -> softwareEncoder(codec);
            };
            case NVENC -> switch (codec) {
                case H264 -> "h264_nvenc";
                case H265 -> "hevc_nvenc";
                case AV1 -> "av1_nvenc";
            };
            case QSV -> switch (codec) {
                case H264 -> "h264_qsv";
                case H265 -> "hevc_qsv";
                case AV1 -> "av1_qsv";
            };
            case VAAPI -> switch (codec) {
                case H264 -> "h264_vaapi";
                case H265 -> "hevc_vaapi";
                case AV1 -> "av1_vaapi";
            };
        };
    }

    private String softwareEncoder(VideoCodec codec) {
        return switch (codec) {
            case H264 -> "libx264";
            case H265 -> "libx265";
            case AV1 -> "libsvtav1";
        };
    }

    private String joinFlags(Set<HlsFlag> flags) {
        final StringJoiner joiner = new StringJoiner("+");
        flags.forEach(flag -> joiner.add(flag.name().toLowerCase(Locale.ROOT)));
        return joiner.toString();
    }

    private String formatSeconds(double seconds) {
        return String.format(Locale.ROOT, "%.3f", seconds);
    }

}
