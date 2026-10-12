package io.github.leonardopinheirolacerda.akari.domain.anibridge.service;

import io.github.leonardopinheirolacerda.akari.api.dto.TmdbMediaType;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.AnimeProvider;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.MappingSegment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;

/**
 * Consulta aos {@link MappingSegment} ingeridos do anibridge — três buscas separadas, uma por
 * id de origem (AniList, MAL) e uma pelo lado TMDB. Devolve a lista crua, sem escolher "melhor
 * candidato" quando há mais de um nem aplicar a conversão episódio-a-episódio — isso é
 * responsabilidade de quem consumir (o ingestion decide o que precisa a partir da lista).
 */
@ApplicationScoped
public class AnibridgeMappingService {

    /**
     * Busca os segments de um anime pelo id na AniList.
     *
     * <p>{@code @Transactional} porque é chamado (via {@code AnibridgeResolutionService} e
     * {@code FolderAutoBindService}) de dentro da thread virtual de reconciliação da ingestão,
     * sem transação ambiente de request HTTP.
     *
     * @param anilistId id do anime na AniList
     * @return os segments encontrados; vazio se não há mapeamento pra esse id
     */
    @Transactional
    public List<MappingSegment> findByAnilistId(Integer anilistId) {
        return MappingSegment.findByAnimeProviderAndId(AnimeProvider.ANILIST, anilistId);
    }

    /**
     * Busca os segments de um anime pelo id no MyAnimeList.
     *
     * <p>Mesmo motivo de {@code @Transactional} de {@link #findByAnilistId}.
     *
     * @param malId id do anime no MyAnimeList
     * @return os segments encontrados; vazio se não há mapeamento pra esse id
     */
    @Transactional
    public List<MappingSegment> findByMalId(Integer malId) {
        return MappingSegment.findByAnimeProviderAndId(AnimeProvider.MAL, malId);
    }

    /**
     * Busca os segments pelo lado TMDB — busca reversa, útil quando quem consulta já sabe o
     * TMDB e quer achar o anime correspondente.
     *
     * @param tmdbId id do recurso no TMDB
     * @param tmdbMediaType tipo do recurso ({@code MOVIE} ou {@code TV})
     * @return os segments encontrados; vazio se não há mapeamento pra esse id/tipo
     */
    @Transactional
    public List<MappingSegment> findByTmdbId(Integer tmdbId, TmdbMediaType tmdbMediaType) {
        return MappingSegment.findByTmdbIdAndMediaType(tmdbId, tmdbMediaType);
    }

}
