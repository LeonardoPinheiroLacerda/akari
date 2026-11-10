package io.github.leonardopinheirolacerda.akari.domain.anime.service;

import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingProposalStatus;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.EpisodeMapping;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.FilePairingResolution;
import io.github.leonardopinheirolacerda.akari.domain.anime.pairing.AirDateEpisodeMapper;
import io.github.leonardopinheirolacerda.akari.domain.anime.pairing.AirDateFolderMapping;
import io.github.leonardopinheirolacerda.akari.domain.anime.pairing.AnibridgeEpisodeMapper;
import io.github.leonardopinheirolacerda.akari.domain.anime.pairing.AnibridgeFolderMapping;
import io.github.leonardopinheirolacerda.akari.domain.anime.pairing.FolderEpisodeNumbering;
import io.github.leonardopinheirolacerda.akari.domain.anime.pairing.TmdbEpisodeValidator;
import io.github.leonardopinheirolacerda.akari.domain.anime.pairing.TmdbSeasonEpisodes;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.service.TmdbService;
import io.github.leonardopinheirolacerda.akari.domain.videofile.model.VideoFile;
import io.github.leonardopinheirolacerda.akari.domain.videofile.service.VideoFileService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Resolve o pairing TMDB dos arquivos de vídeo de um anime vinculado a uma série do TMDB, sem
 * gravar nada — usado pela proposta de pairing do portal ({@link AnimeTmdbProposalService}) e
 * pelo pairing automático da ingestão ({@link AnimeTmdbFilePairingFillService}).
 *
 * <p>Só encadeia as etapas da resolução, cada uma em uma classe de {@code anime/pairing}:
 * <ol>
 *   <li>numeração — o número de episódio de cada arquivo, olhando a pasta inteira
 *       ({@link FolderEpisodeNumbering});</li>
 *   <li>conversão — número de episódio pra temporada/episódio do TMDB, tentando os conversores
 *       em ordem: pelo anibridge, com a numeração absoluta da pasta
 *       ({@link AnibridgeEpisodeMapper}), e, no arquivo que ele não pareou, pelo período de
 *       exibição do anime ({@link AirDateEpisodeMapper});</li>
 *   <li>validação — o episódio existe no TMDB e foi ao ar perto do período de exibição do anime
 *       ({@link TmdbEpisodeValidator}).</li>
 * </ol>
 */
@ApplicationScoped
public class EpisodePairingResolutionService {

    @Inject
    VideoFileService videoFileService;

    @Inject
    TmdbService tmdbService;

    @Inject
    AnibridgeEpisodeMapper anibridgeEpisodeMapper;

    @Inject
    AirDateEpisodeMapper airDateEpisodeMapper;

    @Inject
    TmdbEpisodeValidator tmdbEpisodeValidator;

    /**
     * Resolve o pairing de cada arquivo de vídeo da pasta do anime.
     *
     * <p>O arquivo que a conversão pelo anibridge não pareou — sem mapeamento, ou com episódio
     * inexistente no TMDB ou fora do período de exibição — tenta a conversão pelo período de
     * exibição; se ela também não parear, fica o resultado do anibridge, com o motivo dele. A
     * conversão pelo período só é preparada quando algum arquivo precisa dela.
     *
     * @param anime anime com binding TMDB de série
     * @param folderId pasta do anime — passada à parte pra não depender do lazy load de
     *                 {@code anime.folder}
     * @param folderName nome da pasta, só pra log
     * @return um resultado por arquivo da pasta, na ordem natural dos nomes
     */
    public List<FilePairingResolution> resolve(Anime anime, Integer folderId, String folderName) {
        final List<VideoFile> videoFiles = videoFileService.listByFolderOrderedByNaturalSortKey(
                folderId
        );

        final List<String> fileNames = videoFiles.stream()
                .map(videoFile -> videoFile.name)
                .toList();

        final List<OptionalInt> episodeNumbers = FolderEpisodeNumbering.number(
                fileNames,
                anime.episodes
        );

        final AnibridgeFolderMapping anibridgeMapping = anibridgeEpisodeMapper.prepare(
                anime,
                episodeNumbers,
                videoFiles.size()
        );

        final TmdbSeasonEpisodes seasonEpisodes = new TmdbSeasonEpisodes(
                tmdbService,
                anime.tmdbId,
                folderId,
                folderName
        );

        AirDateFolderMapping airDateMapping = null;

        final List<FilePairingResolution> resolutions = new ArrayList<>();

        for (int i = 0; i < videoFiles.size(); i++) {
            final VideoFile videoFile = videoFiles.get(i);
            final OptionalInt episodeNumber = episodeNumbers.get(i);

            final FilePairingResolution anibridgeResolution = validate(
                    anime,
                    videoFile,
                    anibridgeMapping.map(episodeNumber),
                    seasonEpisodes
            );

            if (anibridgeResolution.status() == TmdbFilePairingProposalStatus.PAIRED
                    || episodeNumber.isEmpty()) {

                resolutions.add(anibridgeResolution);
                continue;
            }

            if (airDateMapping == null) {
                airDateMapping = airDateEpisodeMapper.prepare(anime, seasonEpisodes);
            }

            final FilePairingResolution airDateResolution = validate(
                    anime,
                    videoFile,
                    airDateMapping.map(episodeNumber),
                    seasonEpisodes
            );

            if (airDateResolution.status() == TmdbFilePairingProposalStatus.PAIRED) {
                resolutions.add(airDateResolution);
                continue;
            }

            resolutions.add(anibridgeResolution);
        }

        return resolutions;
    }

    /**
     * Resultado de um arquivo a partir da conversão: sem episódio, o motivo da conversão; com
     * episódio, a validação dele no TMDB.
     */
    private FilePairingResolution validate(Anime anime,
                                           VideoFile videoFile,
                                           EpisodeMapping mapping,
                                           TmdbSeasonEpisodes seasonEpisodes) {

        if (mapping.episode() == null) {
            return FilePairingResolution.unresolved(videoFile, mapping.status());
        }

        return tmdbEpisodeValidator.validate(anime, videoFile, mapping.episode(), seasonEpisodes);
    }

}
