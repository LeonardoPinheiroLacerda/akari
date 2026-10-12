package io.github.leonardopinheirolacerda.akari.domain.mediaprobe.resource;

import io.github.leonardopinheirolacerda.akari.api.MediaProbeApi;
import io.github.leonardopinheirolacerda.akari.api.dto.MediaInfoResponse;
import io.github.leonardopinheirolacerda.akari.domain.mediaprobe.service.MediaProbeService;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationNotConfiguredException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Implementa {@link MediaProbeApi} — probe de metadados técnicos em
 * {@code /v1/mediaprobe/files/{fileId}}.
 *
 * <p>Só traduz HTTP ↔ service, sem lógica própria: toda a resolução do arquivo em disco e a
 * invocação do ffprobe ficam no {@link MediaProbeService}.
 */
@ApplicationScoped
public class MediaProbeResource implements MediaProbeApi {

    @Inject
    MediaProbeService mediaProbeService;

    /**
     * {@code GET /v1/mediaprobe/files/{fileId}} — extrai o probe de um vídeo disponível em
     * disco.
     *
     * @param fileId id do arquivo de vídeo no catalog
     * @return container e streams extraídos pelo ffprobe, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se o {@code fileId} não existir no
     *         catalog, se o arquivo de um provider {@code LOCAL} não existir mais em disco, ou
     *         se o arquivo de um provider remoto ainda não tiver terminado de baixar no cache
     * @throws IntegrationNotConfiguredException (traduzida em 503) se a preferência
     *         {@code cache.dir} não estiver configurada
     * @throws IntegrationException (traduzida em 502) se o ffprobe falhar sobre um arquivo já
     *         resolvido e existente
     */
    @Override
    public MediaInfoResponse getMediaProbe(Integer fileId) {
        return mediaProbeService.getMediaProbe(fileId);
    }
}
