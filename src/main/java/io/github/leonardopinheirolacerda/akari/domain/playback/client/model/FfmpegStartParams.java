package io.github.leonardopinheirolacerda.akari.domain.playback.client.model;

import io.github.leonardopinheirolacerda.akari.api.dto.MediaInfoResponse;
import io.github.leonardopinheirolacerda.akari.domain.playback.client.FfmpegClient;
import io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec.HlsTranscodeSpec;
import io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec.HwAccel;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Parâmetros usados pra montar o comando e subir um processo {@code ffmpeg} de transcode HLS.
 *
 * @param sessionId sessão de playback dona deste processo — usado só pra log e rastreio, não
 *                  entra no comando
 * @param fileId arquivo sendo reproduzido — usado só pra log, não entra no comando
 * @param spec intenção de transcode (vídeo/áudio/HLS) escolhida pela {@code HlsTranscodeStrategy}
 *             vencedora
 * @param inputFile arquivo de vídeo de origem, já resolvido em disco
 * @param workDir diretório onde a playlist e os segmentos desta sessão serão escritos
 * @param startOffsetSeconds valor do {@code -ss} de entrada, em segundos a partir do início da
 *                           mídia; {@code 0} transcodifica do começo. Recodificando o vídeo, é o
 *                           instante onde o transcode começa; copiando, é o keyframe alvo mais
 *                           uma folga ({@code PlaybackService}), e o transcode começa no keyframe
 *                           — não é o {@code startOffsetSeconds} da sessão
 * @param audioTrackIndex posição (0-based) da faixa de áudio entre as faixas de áudio do arquivo
 *                        — o {@code -map} do ffmpeg resolve esse specifier sozinho, sem tradução
 *                        pro índice global do stream
 * @param subtitleTrackIndex posição (0-based) da faixa de legenda entre as faixas de legenda do
 *                           arquivo; {@code null} se nenhuma legenda selecionada
 * @param mediaInfo probe da mídia — o {@link FfmpegClient} resolve nele o codec da legenda
 *                  escolhida, pra decidir soft (WebVTT) vs burn (texto ou imagem)
 * @param hwAccel aceleração de hardware do encoder de vídeo
 */
public record FfmpegStartParams(
        UUID sessionId,
        Integer fileId,
        HlsTranscodeSpec spec,
        Path inputFile,
        Path workDir,
        Double startOffsetSeconds,
        Integer audioTrackIndex,
        Integer subtitleTrackIndex,
        MediaInfoResponse mediaInfo,
        HwAccel hwAccel
) {
}
