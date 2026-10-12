package io.github.leonardopinheirolacerda.akari.domain.anime.resource;

import io.github.leonardopinheirolacerda.akari.api.AnimeTmdbFilePairingApi;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingSchema;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeTmdbFilePairingService;
import io.github.leonardopinheirolacerda.akari.exceptions.BusinessRuleException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Implementa {@link AnimeTmdbFilePairingApi} — pairing TMDB (temporada/episódio/thumbnail) dos
 * arquivos de vídeo, em {@code /v1/metadata/files/{fileId}/tmdb-pairing} e correlatos.
 *
 * <p>Só traduz HTTP ↔ service, sem lógica própria: toda regra de negócio e mapeamento ficam no
 * {@link AnimeTmdbFilePairingService}.
 */
public class AnimeTmdbFilePairingResource implements AnimeTmdbFilePairingApi {

    @Inject
    AnimeTmdbFilePairingService animeTmdbFilePairingService;

    /**
     * {@code DELETE /v1/metadata/files/{fileId}/tmdb-pairing} — remove o pairing do arquivo.
     *
     * @param fileId id do arquivo no catalog
     * @throws ResourceNotFoundException (traduzida em 404) se o arquivo não tiver pairing
     *         gravado
     */
    @Override
    public void deleteTmdbFilePairing(Integer fileId) {
        animeTmdbFilePairingService.deleteTmdbFilePairing(fileId);
    }

    /**
     * {@code GET /v1/metadata/files/{fileId}/tmdb-pairing} — lê o pairing atual de um arquivo.
     *
     * @param fileId id do arquivo no catalog
     * @return o pairing, com {@code 200 OK}; campos {@code null} se o arquivo ainda não tem um
     *         pairing gravado (não é erro)
     * @throws ResourceNotFoundException (traduzida em 404) se o {@code fileId} não existir no
     *         catalog
     */
    @Override
    public TmdbFilePairingResponse getTmdbFilePairing(Integer fileId) {
        return animeTmdbFilePairingService.getTmdbFilePairing(fileId);
    }

    /**
     * {@code GET /v1/metadata/animes/{anilistId}/tmdb/file-pairing/schema} — estrutura da série
     * TMDB vinculada ao anime (temporadas com episódios completos), pro cliente não precisar
     * adivinhar temporada/episódio ao montar o pairing.
     *
     * @param anilistId id do anime na AniList
     * @return o schema com as temporadas e episódios, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se o anime não existir ou não tiver
     *         binding TMDB
     * @throws BusinessRuleException (traduzida em 400) se o binding for de filme — filme não
     *         tem temporadas
     */
    @Override
    public TmdbFilePairingSchema getTmdbFilePairingSchema(Integer anilistId) {
        return animeTmdbFilePairingService.getTmdbFilePairingSchema(anilistId);
    }

    /**
     * {@code GET /v1/metadata/files/tmdb-pairings} — resume os pairings de vários arquivos numa
     * chamada só.
     *
     * @param fileIds ids dos arquivos no catalog
     * @return os pairings existentes, com {@code 200 OK}; arquivos sem pairing gravado não
     *         aparecem no resultado
     */
    @Override
    public List<TmdbFilePairingResponse> getTmdbFilePairings(List<Integer> fileIds) {
        return animeTmdbFilePairingService.getTmdbFilePairings(fileIds);
    }

    /**
     * {@code PUT /v1/metadata/files/{fileId}/tmdb-pairing} — cria ou substitui o pairing do
     * arquivo.
     *
     * @param fileId id do arquivo no catalog
     * @param tmdbFilePairingRequest novo estado do pairing
     * @return o pairing atualizado, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se o {@code fileId} não existir no
     *         catalog
     */
    @Override
    public TmdbFilePairingResponse updateTmdbFilePairing(
            Integer fileId,
            TmdbFilePairingRequest tmdbFilePairingRequest) {
        return animeTmdbFilePairingService.updateTmdbFilePairing(fileId, tmdbFilePairingRequest);
    }
}
