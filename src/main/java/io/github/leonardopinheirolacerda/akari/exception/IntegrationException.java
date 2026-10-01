package io.github.leonardopinheirolacerda.akari.exception;

import io.github.leonardopinheirolacerda.akari.api.dto.ApiErrorCode;

/**
 * Sistema externo (AniList, TMDB, rclone, ffprobe) falhou ou está indisponível — vira {@code 502}
 * com {@code INTEGRATION_ERROR}.
 *
 * <p>A regra de negócio está OK, mas o sistema externo não colaborou: timeout, indisponibilidade,
 * resposta malformada. Integração ainda não configurada é outro caso — use
 * {@link IntegrationNotConfiguredException}. Erros de REST client devem ser embrulhados aqui pelo
 * service; se escaparem crus, saem com o status da resposta upstream.
 */
public class IntegrationException extends AkariException {

    /**
     * Cria a exceção sem causa — para falhas detectadas pelo próprio akari (ex.: resposta upstream
     * sem um campo obrigatório).
     *
     * @param message problema de integração; inclua o sistema envolvido para facilitar o
     *                diagnóstico
     */
    public IntegrationException(String message) {
        this(message, null);
    }

    /**
     * Cria a exceção preservando a falha original — para quando uma chamada ao sistema externo
     * lançou (ex.: {@code ProcessingException} do REST client).
     *
     * @param message problema de integração; inclua o sistema envolvido para facilitar o
     *                diagnóstico
     * @param cause exceção original da chamada, mantida para o stack trace no log
     */
    public IntegrationException(String message, Throwable cause) {
        super(502, ApiErrorCode.INTEGRATION_ERROR, message, cause);
    }
}
