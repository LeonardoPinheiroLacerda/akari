package io.github.leonardopinheirolacerda.akari.domain.anime.resource;

import io.github.leonardopinheirolacerda.akari.api.AnimeTmdbFilePairingApi;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingSchema;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeTmdbFilePairingService;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Pairing TMDB (temporada/episódio/thumbnail) dos arquivos de vídeo.
 */
public class AnimeTmdbFilePairingResource implements AnimeTmdbFilePairingApi {

    @Inject
    AnimeTmdbFilePairingService animeTmdbFilePairingService;

    @Override
    public void deleteTmdbFilePairing(Integer fileId) {
        animeTmdbFilePairingService.deleteTmdbFilePairing(fileId);
    }

    @Override
    public TmdbFilePairingResponse getTmdbFilePairing(Integer fileId) {
        return animeTmdbFilePairingService.getTmdbFilePairing(fileId);
    }

    @Override
    public TmdbFilePairingSchema getTmdbFilePairingSchema(Integer anilistId) {
        return animeTmdbFilePairingService.getTmdbFilePairingSchema(anilistId);
    }

    @Override
    public List<TmdbFilePairingResponse> getTmdbFilePairings(List<Integer> fileIds) {
        return animeTmdbFilePairingService.getTmdbFilePairings(fileIds);
    }

    @Override
    public TmdbFilePairingResponse updateTmdbFilePairing(
            Integer fileId,
            TmdbFilePairingRequest tmdbFilePairingRequest) {
        return animeTmdbFilePairingService.updateTmdbFilePairing(fileId, tmdbFilePairingRequest);
    }
}
