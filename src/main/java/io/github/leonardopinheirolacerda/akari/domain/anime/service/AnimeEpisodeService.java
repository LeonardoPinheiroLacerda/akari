package io.github.leonardopinheirolacerda.akari.domain.anime.service;

import io.github.leonardopinheirolacerda.akari.api.dto.VideoFilePage;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.videofile.service.VideoFileService;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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

    /**
     * Pagina os episódios (arquivos de vídeo) do anime, em ordem natural de {@code name}
     * (trechos numéricos comparam como número, não caractere a caractere). O pareamento TMDB
     * (quando houver) só enriquece o item em {@code tmdbPairing} — não define a posição.
     *
     * @param anilistId id do anime na AniList
     * @param page página desejada, 0-based
     * @param size tamanho da página
     * @return página de episódios; vazia se a pasta do anime não tiver arquivos
     * @throws ResourceNotFoundException se o {@code anilistId} não existir
     */
    public VideoFilePage listAnimeEpisodes(Integer anilistId, Integer page, Integer size) {
        final Anime anime = animeService.findOrThrow(anilistId);

        return videoFileService.listVideoFilesOrderedByNaturalSortKey(anime.folder.id, page, size);
    }

}
