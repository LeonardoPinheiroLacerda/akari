package io.github.leonardopinheirolacerda.akari.domain.anime.resource;

import io.github.leonardopinheirolacerda.akari.api.AnimeEpisodesApi;
import io.github.leonardopinheirolacerda.akari.api.dto.VideoFilePage;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeEpisodeService;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Implementa {@link AnimeEpisodesApi} — episódios do anime, prontos pra tela de episódios, em
 * {@code /v1/metadata/animes/{anilistId}/episodes}.
 *
 * <p>Só traduz HTTP ↔ service, sem lógica própria: toda regra fica no
 * {@link AnimeEpisodeService}.
 */
@ApplicationScoped
public class AnimeEpisodeResource implements AnimeEpisodesApi {

    @Inject
    AnimeEpisodeService animeEpisodeService;

    /**
     * {@code GET /v1/metadata/animes/{anilistId}/episodes} — pagina os episódios do anime, já
     * ordenados por temporada e episódio.
     *
     * @param anilistId id do anime na AniList
     * @param page página desejada, 0-based
     * @param size tamanho da página
     * @param userId id do usuário cujo progresso marca os episódios (opcional)
     * @return página de episódios, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se o {@code anilistId} ou o
     *         {@code userId} informado não existir
     */
    @Override
    public VideoFilePage listAnimeEpisodes(Integer anilistId, Integer page, Integer size, Integer userId) {
        return animeEpisodeService.listAnimeEpisodes(anilistId, page, size, userId);
    }
}
