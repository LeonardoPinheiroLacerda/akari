package io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response do endpoint {@code /core/stats} do rclone — snapshot da transferência em curso.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} porque o rclone expõe muitos outros
 * campos que não interessam aqui.
 *
 * @param bytes bytes transferidos até o momento
 * @param totalBytes total esperado de bytes (pode ser {@code null} enquanto o rclone descobre)
 * @param speed velocidade instantânea em bytes/segundo
 * @param eta segundos restantes estimados; {@code null} enquanto indeterminado
 * @param transfers quantidade de transferências ativas
 * @param errors quantidade acumulada de erros encontrados
 * @param fatalError {@code true} se ocorreu erro que interrompe o grupo por completo
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RcloneDownloadStatsResponse(
        Long bytes,
        Long totalBytes,
        Double speed,
        Long eta,
        Integer transfers,
        Integer errors,
        Boolean fatalError
) {

    /**
     * Calcula o percentual concluído da transferência.
     *
     * <p>Blindado contra divisão por zero e valores ausentes: retorna {@code 0.0} se
     * {@code bytes} ou {@code totalBytes} forem nulos ou se {@code totalBytes} for zero.
     * Nunca ultrapassa {@code 100.0} mesmo se o rclone reportar {@code bytes > totalBytes}.
     *
     * @return percentual entre {@code 0.0} e {@code 100.0}
     */
    public double getPercentage() {
        if (bytes == null || totalBytes == null || totalBytes == 0L) {
            return 0.0;
        }

        final double percentage = ((double) bytes / totalBytes) * 100.0;
        return Math.min(percentage, 100.0);
    }
}
