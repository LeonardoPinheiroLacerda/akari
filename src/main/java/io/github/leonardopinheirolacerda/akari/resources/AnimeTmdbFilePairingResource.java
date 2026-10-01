package io.github.leonardopinheirolacerda.akari.resources;

import io.github.leonardopinheirolacerda.akari.api.AnimeTmdbFilePairingApi;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingSchema;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.util.List;

public class AnimeTmdbFilePairingResource implements AnimeTmdbFilePairingApi {

    // 404: arquivo sem pairing gravado.
    @Override
    public void deleteTmdbFilePairing(Integer fileId) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    // Arquivo sem pairing NÃO é 404: responde 200 com seasonNumber, episodeNumber e
    // thumbnailPath null.
    // 404 só quando o fileId não existe no catalog.
    @Override
    public TmdbFilePairingResponse getTmdbFilePairing(Integer fileId) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    // 404: anime sem binding TMDB.
    // 400 BUSINESS_RULE_VIOLATION: binding de filme — só série tem temporadas/episódios.
    @Override
    public TmdbFilePairingSchema getTmdbFilePairingSchema(Integer anilistId) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    @Override
    public List<TmdbFilePairingResponse> getTmdbFilePairings(List<Integer> fileIds) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    // 404: fileId inexistente no catalog.
    @Override
    public TmdbFilePairingResponse updateTmdbFilePairing(
            Integer fileId,
            TmdbFilePairingRequest tmdbFilePairingRequest) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }
}
