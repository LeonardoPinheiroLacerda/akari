package io.github.leonardopinheirolacerda.akari.domain.anime.service;

import io.github.leonardopinheirolacerda.akari.api.dto.VideoFilePage;
import io.github.leonardopinheirolacerda.akari.api.dto.VideoFileResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.WatchProgress;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.user.service.UserService;
import io.github.leonardopinheirolacerda.akari.domain.videofile.service.VideoFileService;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;

/**
 * Listagem de episódios de um anime, pronta pra tela de episódios: resolve a pasta vinculada e
 * pagina os arquivos de vídeo dela em ordem natural de nome, poupando o cliente de descobrir o
 * {@code folderId} ou reordenar.
 */
@ApplicationScoped
public class AnimeEpisodeService {

    @Inject
    AnimeService animeService;

    @Inject
    VideoFileService videoFileService;

    @Inject
    UserService userService;

    /**
     * Pagina os episódios (arquivos de vídeo) do anime, em ordem natural de {@code name}
     * (trechos numéricos comparam como número, não caractere a caractere). O pareamento TMDB
     * (quando houver) só enriquece o item em {@code tmdbPairing} — não define a posição. Com
     * {@code userId}, cada episódio vem com o {@code watchProgress} do usuário.
     *
     * @param anilistId id do anime na AniList
     * @param page página desejada, 0-based
     * @param size tamanho da página
     * @param userId id do usuário cujo progresso marca os episódios; {@code null} não marca
     * @return página de episódios; vazia se a pasta do anime não tiver arquivos
     * @throws ResourceNotFoundException se o {@code anilistId} ou o {@code userId} informado não
     *         existir
     */
    public VideoFilePage listAnimeEpisodes(Integer anilistId, Integer page, Integer size, Integer userId) {
        final Anime anime = animeService.findOrThrow(anilistId);

        final VideoFilePage episodes = videoFileService.listVideoFilesOrderedByNaturalSortKey(anime.folder.id, page, size);

        if (userId == null) {
            return episodes;
        }

        final List<Integer> fileIds = episodes.getData()
                .stream()
                .map(VideoFileResponse::getId)
                .toList();

        final Map<Integer, WatchProgress> progressByFileId = userService.findWatchProgressByFiles(userId, fileIds);

        for (VideoFileResponse episode : episodes.getData()) {
            episode.setWatchProgress(progressByFileId.get(episode.getId()));
        }

        return episodes;
    }

}
