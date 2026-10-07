package io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.dtos;

/**
 * Response do endpoint {@code /operations/copyfile} quando chamado em modo assíncrono.
 *
 * @param jobid identificador do job criado; usado para consultar status em {@code /job/status}
 *              e stats em {@code /core/stats?group=job/<jobid>}
 */
public record RcloneCopyFileResponse(
        Long jobid
) {
}
