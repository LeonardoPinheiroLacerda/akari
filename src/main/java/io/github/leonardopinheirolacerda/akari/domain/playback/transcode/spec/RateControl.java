package io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec;

/**
 * Controle de taxa/qualidade do vídeo. As combinações determinam o modo: só {@code crf} =
 * qualidade constante; {@code bitrateKbps} + {@code maxrateKbps} + {@code bufsizeKb} = VBR com
 * teto; {@code crf} + {@code maxrateKbps} = <em>capped CRF</em> (recomendado para HLS VOD).
 *
 * @param crf qualidade constante (0–51; menor = melhor); {@code null} se não usa CRF
 * @param bitrateKbps bitrate-alvo em kbps; {@code null} se não usa bitrate-alvo
 * @param maxrateKbps teto de bitrate em kbps (limita picos); {@code null} se sem teto
 * @param bufsizeKb buffer do rate control em kb; acompanha o {@code maxrateKbps}
 */
public record RateControl(
        Integer crf,
        Integer bitrateKbps,
        Integer maxrateKbps,
        Integer bufsizeKb
) {
}
