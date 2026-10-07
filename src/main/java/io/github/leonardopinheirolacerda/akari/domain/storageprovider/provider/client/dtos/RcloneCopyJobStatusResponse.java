package io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response do endpoint {@code /job/status} do rclone — descreve o ciclo de vida do job.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} pra não quebrar se o rclone
 * introduzir novos campos.
 *
 * @param duration duração da execução em nanosegundos (ou {@code null} se ainda não terminou)
 * @param error mensagem de erro caso o job tenha falhado; {@code null} ou vazio se OK
 * @param finished {@code true} se o job terminou (com sucesso ou falha)
 * @param id id do job (mesmo valor do jobid da request)
 * @param startTime instante de início em formato ISO-8601 gerado pelo rclone
 * @param success {@code true} se terminou sem erro
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RcloneCopyJobStatusResponse(
        Long duration,
        String error,
        boolean finished,
        Long id,
        String startTime,
        boolean success
) {
}
