package io.github.leonardopinheirolacerda.akari.resources;

import io.github.leonardopinheirolacerda.akari.api.MediaProbeApi;
import io.github.leonardopinheirolacerda.akari.api.dto.MediaInfoResponse;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class MediaProbeResource implements MediaProbeApi {

    // 404: arquivo de provider remoto ainda não terminou de baixar no cache.
    // 502: falha do ffprobe sobre um arquivo existente.
    @Override
    public MediaInfoResponse getMediaProbe(Integer fileId) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }
}
