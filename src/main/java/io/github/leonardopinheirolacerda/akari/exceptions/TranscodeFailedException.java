package io.github.leonardopinheirolacerda.akari.exceptions;

import io.github.leonardopinheirolacerda.akari.api.dto.ApiErrorCode;

/**
 * O transcode de uma sessão de playback morreu antes de gerar a playlist (ou depois de já ter
 * gerado) e não vai se recuperar — vira {@code 500} com {@code TRANSCODE_FAILED}.
 *
 * <p>Diferente de {@link IntegrationException}, não é o {@code ffmpeg} que não pôde ser iniciado
 * (falha de integração) — é o processo que chegou a rodar e morreu por um motivo só visível no
 * stderr dele, já logado como {@code ERROR} por quem detectou a morte. Sinaliza ao cliente para
 * parar de tentar a playlist e mostrar erro, em vez do {@code 404} de "ainda não gerada".
 */
public class TranscodeFailedException extends AkariException {

    /**
     * Cria a exceção para um transcode que falhou.
     *
     * @param message explicação legível para o consumidor da API; o motivo detalhado fica só no
     *                log do servidor
     */
    public TranscodeFailedException(String message) {
        super(500, ApiErrorCode.TRANSCODE_FAILED, message, null);
    }
}
