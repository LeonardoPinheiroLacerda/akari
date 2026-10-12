package io.github.leonardopinheirolacerda.akari.domain.mediaprobe.client;

/**
 * Falha ao invocar ou ler a saída do {@code ffprobe}.
 *
 * <p>Não é uma {@code AkariException} — é um sinal interno entre {@link FfprobeClient} e
 * {@code MediaProbeService}, que sempre a captura e a reembrulha em {@code IntegrationException}
 * (502), preservando a mensagem específica de cada causa (binário ausente, timeout, código de
 * saída não-zero, JSON inválido, interrupção).
 */
public class FfprobeException extends RuntimeException {

    /**
     * Cria a exceção sem causa — para falhas detectadas pelo próprio probe (ex.: código de
     * saída não-zero, timeout).
     *
     * @param message descrição da falha, já pronta para virar a mensagem da
     *                {@code IntegrationException}
     */
    public FfprobeException(String message) {
        super(message);
    }

    /**
     * Cria a exceção preservando a falha original — para quando uma chamada de I/O lançou
     * (ex.: {@code IOException} ao iniciar o processo ou ler a saída).
     *
     * @param message descrição da falha, já pronta para virar a mensagem da
     *                {@code IntegrationException}
     * @param cause exceção original, mantida para o stack trace no log
     */
    public FfprobeException(String message, Throwable cause) {
        super(message, cause);
    }

}
