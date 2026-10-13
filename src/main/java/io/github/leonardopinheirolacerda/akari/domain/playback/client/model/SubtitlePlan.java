package io.github.leonardopinheirolacerda.akari.domain.playback.client.model;

/**
 * Resultado da decisão de como a legenda escolhida pra sessão entra no comando {@code ffmpeg}.
 *
 * @param mode modo de entrega da legenda
 * @param trackIndex índice da faixa de legenda no arquivo de entrada; {@code null} se {@code mode}
 *                   for {@link SubtitleMode#NONE}
 * @param burnFilter filtro {@code subtitles} pronto pra cadeia de vídeo; {@code null} se {@code mode}
 *                    não for {@link SubtitleMode#BURN}
 */
public record SubtitlePlan(SubtitleMode mode, Integer trackIndex, String burnFilter) {

    /** {@code true} se a legenda é entregue como faixa WebVTT soft, dentro do HLS. */
    public boolean isSoft() {
        return mode == SubtitleMode.SOFT;
    }

}
