package io.github.leonardopinheirolacerda.akari.domain.anime.model;

import io.github.leonardopinheirolacerda.akari.api.dto.FranchiseRootCuration;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbMediaType;
import io.github.leonardopinheirolacerda.akari.domain.mediafolder.model.MediaFolder;
import io.github.leonardopinheirolacerda.akari.model.PageResult;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Metadata local de um anime, vinculada 1:1 a uma pasta do catalog. A chave primária é o próprio
 * {@code anilistId} — não existe id interno separado.
 */
@Entity
@Table(name = "anime")
public class Anime extends PanacheEntityBase {

    @Id
    @Column(name = "anilist_id")
    public Integer anilistId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "folder_id", unique = true)
    public MediaFolder folder;

    @Column(name = "title_main")
    public String titleMain;

    @Column(name = "title_english")
    public String titleEnglish;

    @Column(name = "title_japanese")
    public String titleJapanese;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "title_synonyms")
    public List<String> titleSynonyms;

    @Column(name = "synopsis", columnDefinition = "text")
    public String synopsis;

    @Column(name = "thumbnail_small")
    public String thumbnailSmall;

    @Column(name = "thumbnail_medium")
    public String thumbnailMedium;

    @Column(name = "thumbnail_large")
    public String thumbnailLarge;

    public Integer episodes;

    public String duration;

    @Column(name = "season_year")
    public Integer seasonYear;

    public String format;

    public String season;

    public Double score;

    @Column(name = "is_adult")
    public Boolean isAdult;

    @JdbcTypeCode(SqlTypes.JSON)
    public List<String> genres;

    @Enumerated(EnumType.STRING)
    @Column(name = "franchise_root_curation")
    public FranchiseRootCuration franchiseRootCuration;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public OffsetDateTime createdAt;

    @Column(name = "tmdb_id")
    public Integer tmdbId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tmdb_media_type")
    public TmdbMediaType tmdbMediaType;

    @Column(name = "tmdb_bound_at")
    public OffsetDateTime tmdbBoundAt;

    @Column(name = "override_title")
    public String overrideTitle;

    @Column(name = "override_synopsis", columnDefinition = "text")
    public String overrideSynopsis;

    @Column(name = "override_poster_path")
    public String overridePosterPath;

    @Column(name = "override_backdrop_path")
    public String overrideBackdropPath;

    @Column(name = "override_logo_path")
    public String overrideLogoPath;

    /**
     * Busca pelo {@code anilistId}, a própria chave primária.
     *
     * @param anilistId id do anime na AniList
     * @return o anime, se existir metadata salva
     */
    public static Optional<Anime> find(Integer anilistId) {
        return Anime
                .find("anilistId", anilistId)
                .firstResultOptional();
    }

    /**
     * Busca pela pasta vinculada.
     *
     * @param folderId id da pasta no catalog
     * @return o anime vinculado a essa pasta, se houver
     */
    public static Optional<Anime> findByFolderId(Integer folderId) {
        return Anime
                .find("folder.id", folderId)
                .firstResultOptional();
    }

    /**
     * Busca os animes vinculados a qualquer uma das pastas informadas.
     *
     * @param folderIds ids das pastas no catalog
     * @return os animes vinculados; pastas sem binding simplesmente não aparecem
     */
    public static List<Anime> findByFolderIds(List<Integer> folderIds) {
        return Anime
                .find("folder.id in ?1", folderIds)
                .list();
    }

    /**
     * Animes-raiz da coleção, paginados: curadoria manual em {@code MANUAL_ROOT}, mais os em
     * {@code AUTO} dos quais não sai nenhuma relação ancestral ({@link RelationType#ANCESTOR_LINKS}).
     *
     * @param page página 0-based
     * @param size tamanho da página
     * @return a página de raízes, ordenada por {@code anilistId}
     */
    public static PageResult<Anime> findRoots(Integer page, Integer size) {
        final PanacheQuery<Anime> query = Anime
                .find(
                        "FROM Anime a WHERE a.franchiseRootCuration = ?1"
                                + " OR (a.franchiseRootCuration = ?2 AND NOT EXISTS ("
                                + "   SELECT 1 FROM AnimeRelation r"
                                + "   WHERE r.fromAnilistId = a.anilistId AND r.relationType IN ?3"
                                + " ))"
                                + " ORDER BY a.anilistId",
                        FranchiseRootCuration.MANUAL_ROOT,
                        FranchiseRootCuration.AUTO,
                        RelationType.ANCESTOR_LINKS
                )
                .page(page, size);

        return new PageResult<>(
                query.list(),
                page,
                size,
                query.count(),
                query.pageCount()
        );
    }

}
