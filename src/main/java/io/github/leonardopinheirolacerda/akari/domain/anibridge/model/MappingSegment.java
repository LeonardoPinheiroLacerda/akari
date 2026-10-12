package io.github.leonardopinheirolacerda.akari.domain.anibridge.model;

import io.github.leonardopinheirolacerda.akari.api.dto.TmdbMediaType;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;

/**
 * Um range de episódios que liga um anime (por {@code animeProvider} + {@code animeId}) a uma
 * temporada/episódios do TMDB. Cada refresh do dataset do anibridge substitui todos os
 * segments existentes.
 *
 * <p>{@code animeEpisodeEnd}/{@code tmdbEpisodeEnd} ficam {@code null} quando o range é aberto
 * no dataset de origem (ex.: {@code "1089-"}, sem episódio final conhecido). {@code seasonNumber}
 * fica {@code null} pra {@link TmdbMediaType#MOVIE}
 * (filme não tem temporada). {@code ratio}, quando presente, descreve quantos episódios de um
 * lado correspondem a um episódio do outro — a conversão em si é responsabilidade de quem
 * consome o segment, não é aplicada aqui.
 */
@Entity
@Table(name = "anibridge_mapping_segment")
public class MappingSegment extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;

    @Enumerated(EnumType.STRING)
    @Column(name = "anime_provider")
    public AnimeProvider animeProvider;

    @Column(name = "anime_id")
    public Integer animeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tmdb_media_type")
    public TmdbMediaType tmdbMediaType;

    @Column(name = "tmdb_id")
    public Integer tmdbId;

    @Column(name = "season_number")
    public Integer seasonNumber;

    @Column(name = "anime_episode_start")
    public Integer animeEpisodeStart;

    @Column(name = "anime_episode_end")
    public Integer animeEpisodeEnd;

    @Column(name = "tmdb_episode_start")
    public Integer tmdbEpisodeStart;

    @Column(name = "tmdb_episode_end")
    public Integer tmdbEpisodeEnd;

    @Column(name = "ratio")
    public Integer ratio;

    /**
     * Busca os segments de um anime pela fonte e id.
     *
     * @param animeProvider fonte do id ({@link AnimeProvider#ANILIST} ou {@link AnimeProvider#MAL})
     * @param animeId id do anime na fonte
     * @return os segments encontrados
     */
    public static List<MappingSegment> findByAnimeProviderAndId(AnimeProvider animeProvider, Integer animeId) {
        return MappingSegment
                .find("animeProvider = ?1 and animeId = ?2", animeProvider, animeId)
                .list();
    }

    /**
     * Busca os segments pelo lado TMDB — busca reversa, útil quando quem consulta já sabe o
     * TMDB e quer achar o anime correspondente.
     *
     * @param tmdbId id do recurso no TMDB
     * @param tmdbMediaType tipo do recurso
     * @return os segments encontrados
     */
    public static List<MappingSegment> findByTmdbIdAndMediaType(Integer tmdbId, TmdbMediaType tmdbMediaType) {
        return MappingSegment
                .find("tmdbId = ?1 and tmdbMediaType = ?2", tmdbId, tmdbMediaType)
                .list();
    }

}