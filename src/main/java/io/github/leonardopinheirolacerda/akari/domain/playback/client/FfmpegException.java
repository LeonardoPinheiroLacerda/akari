package io.github.leonardopinheirolacerda.akari.domain.playback.client;

/**
 * Falha ao iniciar o processo {@code ffmpeg} de transcode.
 *
 * <p>Não é uma {@code AkariException} — é um sinal interno entre {@link FfmpegClient} e o
 * {@code PlaybackService}, que sempre a captura e a reembrulha em {@code IntegrationException}
 * (502), preservando a mensagem específica da causa.
 */
public class FfmpegException extends RuntimeException {

    /**
     * Cria a exceção sem causa.
     *
     * @param message descrição da falha, já pronta para virar a mensagem da
     *                {@code IntegrationException}
     */
    public FfmpegException(String message) {
        super(message);
    }

    /**
     * Cria a exceção preservando a falha original.
     *
     * @param message descrição da falha, já pronta para virar a mensagem da
     *                {@code IntegrationException}
     * @param cause exceção original, mantida para o stack trace no log
     */
    public FfmpegException(String message, Throwable cause) {
        super(message, cause);
    }

}
