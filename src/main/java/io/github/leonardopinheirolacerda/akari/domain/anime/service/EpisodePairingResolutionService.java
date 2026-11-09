package io.github.leonardopinheirolacerda.akari.domain.anime.service;

import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.EpisodeMapping;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.FilePairingResolution;
import io.github.leonardopinheirolacerda.akari.domain.anime.pairing.AnibridgeEpisodeMapper;
import io.github.leonardopinheirolacerda.akari.domain.anime.pairing.AnibridgeFolderMapping;
import io.github.leonardopinheirolacerda.akari.domain.anime.pairing.FolderEpisodeNumbering;
import io.github.leonardopinheirolacerda.akari.domain.anime.pairing.TmdbEpisodeValidator;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbEpisodeResponse;
import io.github.leonardopinheirolacerda.akari.domain.videofile.model.VideoFile;
import io.github.leonardopinheirolacerda.akari.domain.videofile.service.VideoFileService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 *   <li>conversão — número de episódio pra temporada/episódio do TMDB pelo anibridge, com a
 *       numeração absoluta da pasta ({@link AnibridgeEpisodeMapper});</li>
 *   <li>validação — o episódio existe no TMDB e foi ao ar perto do período de exibição do anime
 *       ({@link TmdbEpisodeValidator}).</li>
 * </ol>
 */
@ApplicationScoped
public class EpisodePairingResolutionService {

    @Inject
    VideoFileService videoFileService;

    @Inject
    AnibridgeEpisodeMapper anibridgeEpisodeMapper;

    @Inject
    TmdbEpisodeValidator tmdbEpisodeValidator;

    /**
     * Resolve o pairing de cada arquivo de vídeo da pasta do anime.
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

        final AnibridgeFolderMapping folderMapping = anibridgeEpisodeMapper.prepare(
                anime,
                episodeNumbers,
                videoFiles.size()
        );

        final Map<Integer, List<TmdbEpisodeResponse>> episodesBySeason = new HashMap<>();
        final List<FilePairingResolution> resolutions = new ArrayList<>();

        for (int i = 0; i < videoFiles.size(); i++) {
            final VideoFile videoFile = videoFiles.get(i);
            final EpisodeMapping mapping = folderMapping.map(episodeNumbers.get(i));

            if (mapping.episode() == null) {
                resolutions.add(FilePairingResolution.unresolved(videoFile, mapping.status()));
                continue;
            }

            resolutions.add(tmdbEpisodeValidator.validate(
                    anime,
                    videoFile,
                    mapping.episode(),
                    episodesBySeason,
                    folderId,
                    folderName
            ));
        }

        return resolutions;
    }

}
