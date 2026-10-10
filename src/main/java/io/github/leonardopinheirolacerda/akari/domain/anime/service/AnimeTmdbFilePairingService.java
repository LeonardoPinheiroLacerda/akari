package io.github.leonardopinheirolacerda.akari.domain.anime.service;

import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingSchema;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbMediaType;
import io.github.leonardopinheirolacerda.akari.domain.anime.mapper.AnimeTmdbFilePairingMapper;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.TmdbFilePairing;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbSeasonResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbSeasonSummaryResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.service.TmdbSearchService;
import io.github.leonardopinheirolacerda.akari.domain.videofile.model.VideoFile;
import io.github.leonardopinheirolacerda.akari.domain.videofile.service.VideoFileService;
import io.github.leonardopinheirolacerda.akari.exceptions.BusinessRuleException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;

/**
 * Pairing TMDB (temporada/episódio/thumbnail) dos arquivos de vídeo, e a estrutura de
 * temporadas/episódios da série vinculada, usada pelo modal de pairing.
 */
@ApplicationScoped
public class AnimeTmdbFilePairingService {

    @Inject
    AnimeTmdbFilePairingMapper animeTmdbFilePairingMapper;

    @Inject
    AnimeTmdbService animeTmdbService;

    @Inject
    VideoFileService videoFileService;

    @Inject
    TmdbSearchService tmdbSearchService;

    /**
     * Estrutura da série TMDB vinculada ao anime — temporadas com episódios completos, pro
     * modal de pairing não precisar adivinhar temporada/episódio.
     *
     * @param anilistId id do anime na AniList
     * @return o schema com as temporadas e episódios
     * @throws ResourceNotFoundException se o anime não existir ou não tiver binding TMDB
     * @throws BusinessRuleException se o binding for de filme — filme não tem temporadas
     */
    public TmdbFilePairingSchema getTmdbFilePairingSchema(Integer anilistId) {
        final Anime anime = animeTmdbService.findBoundAnimeOrThrow(anilistId);

        if (anime.tmdbMediaType != TmdbMediaType.TV) {
            Log.warnf("Anime %d tem binding de filme — sem temporadas pra parear", anilistId);
            throw new BusinessRuleException("Esse anime é um filme — não tem temporadas nem episódios pra parear");
        }

        final List<TmdbSeasonSummaryResponse> seasonSummaries = tmdbSearchService
                .getTvDetails(anime.tmdbId, false)
                .seasons();

        final List<TmdbSeasonResponse> seasons = seasonSummaries.stream()
                .map(summary -> tmdbSearchService.getSeason(anime.tmdbId, summary.seasonNumber(), false))
                .toList();

        return animeTmdbFilePairingMapper.toSchema(seasons);
    }

    /**
     * Lê o pairing atual de um arquivo. Arquivo sem pairing gravado responde com
     * {@code seasonNumber}/{@code episodeNumber}/{@code thumbnailPath} {@code null} — não é
     * erro.
     *
     * @param fileId id do arquivo no catalog
     * @return o pairing, ou um pairing vazio se o arquivo ainda não tem um gravado
     * @throws ResourceNotFoundException se o {@code fileId} não existir no catalog
     */
    public TmdbFilePairingResponse getTmdbFilePairing(Integer fileId) {
        videoFileService.findOrThrow(fileId);

        return TmdbFilePairing.find(fileId)
                .map(animeTmdbFilePairingMapper::toResponse)
                .orElseGet(() -> new TmdbFilePairingResponse().fileId(fileId));
    }

    /**
     * Resume os pairings de vários arquivos numa chamada só. Arquivos sem pairing gravado
     * simplesmente não aparecem no resultado.
     *
     * @param fileIds ids dos arquivos no catalog
     * @return os pairings existentes
     */
    public List<TmdbFilePairingResponse> getTmdbFilePairings(List<Integer> fileIds) {
        return TmdbFilePairing.findByFileIds(fileIds)
                .stream()
                .map(animeTmdbFilePairingMapper::toResponse)
                .toList();
    }

    /**
     * Cria ou substitui o pairing do arquivo. Campos {@code null} no payload zeram aquela
     * dimensão, mas a linha continua existindo — pra remover a linha inteira use
     * {@link #deleteTmdbFilePairing(Integer)}.
     *
     * @param fileId id do arquivo no catalog
     * @param tmdbFilePairingRequest novo estado do pairing
     * @return o pairing atualizado
     * @throws ResourceNotFoundException se o {@code fileId} não existir no catalog
     */
    @Transactional
    public TmdbFilePairingResponse updateTmdbFilePairing(Integer fileId, TmdbFilePairingRequest tmdbFilePairingRequest) {
        Log.infof("Atualizando pairing TMDB do arquivo %d", fileId);

        final VideoFile videoFile = videoFileService.findOrThrow(fileId);

        final TmdbFilePairing pairing = TmdbFilePairing.find(fileId)
                .orElseGet(() -> {
                    final TmdbFilePairing created = new TmdbFilePairing();
                    created.fileId = videoFile.id;
                    return created;
                });

        pairing.seasonNumber = tmdbFilePairingRequest.getSeasonNumber();
        pairing.episodeNumber = tmdbFilePairingRequest.getEpisodeNumber();
        pairing.thumbnailPath = tmdbFilePairingRequest.getThumbnailPath();

        pairing.persist();

        Log.infof("Pairing TMDB do arquivo %d atualizado com sucesso", fileId);

        return animeTmdbFilePairingMapper.toResponse(pairing);
    }

    /**
     * Remove o pairing do arquivo.
     *
     * @param fileId id do arquivo no catalog
     * @throws ResourceNotFoundException se o arquivo não tiver pairing gravado
     */
    @Transactional
    public void deleteTmdbFilePairing(Integer fileId) {
        Log.infof("Removendo pairing TMDB do arquivo %d", fileId);

        final TmdbFilePairing pairing = TmdbFilePairing.find(fileId)
                .orElseThrow(() -> {
                    Log.warnf("Arquivo %d não tem pairing TMDB gravado", fileId);
                    return new ResourceNotFoundException(
                            "Não foi possível localizar um pairing TMDB para o arquivo informado");
                });

        pairing.delete();

        Log.infof("Pairing TMDB do arquivo %d removido com sucesso", fileId);
    }

}
