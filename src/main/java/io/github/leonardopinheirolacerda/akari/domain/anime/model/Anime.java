package io.github.leonardopinheirolacerda.akari.domain.anime.model;

import io.github.leonardopinheirolacerda.akari.api.dto.AnimeReviewStatus;
import io.github.leonardopinheirolacerda.akari.api.dto.FranchiseAssignment;
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
import jakarta.persistence.ManyToOne;
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

    @Column(name = "mal_id")
    public Integer malId;

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

    public String studio;

    @Enumerated(EnumType.STRING)
    @Column(name = "franchise_assignment")
    public FranchiseAssignment franchiseAssignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "franchise_root_id")
    public Anime franchiseRoot;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status")
    public AnimeReviewStatus reviewStatus;

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
     * Dados de franquia de todos os animes da coleção, numa consulta só — base da resolução de
     * franquias ({@code FranchiseResolver}).
     *
     * @return um {@link FranchiseNode} por anime
     */
    public static List<FranchiseNode> findFranchiseNodes() {
        return Anime
                .find(
                        "select a.anilistId, a.seasonYear, a.franchiseAssignment, r.anilistId"
                                + " from Anime a left join a.franchiseRoot r"
                )
                .project(FranchiseNode.class)
                .list();
    }

    /**
     * Ids dos animes cujo título principal, inglês ou manual contém o texto, sem diferenciar
     * maiúsculas.
     *
     * @param text texto buscado
     * @return os {@code anilistId} encontrados
     */
    public static List<Integer> findIdsByTitle(String text) {
        return Anime
                .find(
                        "select anilistId from Anime"
                                + " where lower(titleMain) like ?1"
                                + " or lower(titleEnglish) like ?1"
                                + " or lower(overrideTitle) like ?1",
                        "%" + text.toLowerCase() + "%"
                )
                .project(Integer.class)
                .list();
    }

    /**
     * Página dos animes com os ids informados, ordenada por {@code anilistId} — pra paginar no
     * banco um conjunto de ids decidido em memória (raízes, ocultos).
     *
     * @param anilistIds ids que compõem o conjunto paginado
     * @param page página 0-based
     * @param size tamanho da página
     * @return a página de animes
     */
    public static PageResult<Anime> findPageByIds(List<Integer> anilistIds, Integer page, Integer size) {
        if (anilistIds.isEmpty()) {
            return new PageResult<>(List.of(), page, size, 0L, 0);
        }

        final PanacheQuery<Anime> query = Anime
                .find("anilistId in ?1 order by anilistId", anilistIds)
                .page(page, size);

        return new PageResult<>(
                query.list(),
                page,
                size,
                query.count(),
                query.pageCount()
        );
    }

    /**
     * Passa pra {@code target} os animes {@code MANUAL_MEMBER} na franquia de {@code source}.
     *
     * @param source raiz de onde os membros saem
     * @param target raiz pra onde os membros vão
     */
    public static void moveManualMembers(Anime source, Anime target) {
        Anime.update(
                "franchiseRoot = ?1 where franchiseAssignment = ?2 and franchiseRoot = ?3",
                target,
                FranchiseAssignment.MANUAL_MEMBER,
                source
        );
    }

    /**
     * Volta pra {@code AUTO} os animes {@code MANUAL_MEMBER} na franquia de {@code root} — usado ao
     * remover a franquia manual ou o próprio anime raiz.
     *
     * @param root raiz cujos membros manuais voltam pra regra automática
     */
    public static void resetManualMembers(Anime root) {
        Anime.update(
                "franchiseAssignment = ?1, franchiseRoot = null"
                        + " where franchiseAssignment = ?2 and franchiseRoot = ?3",
                FranchiseAssignment.AUTO,
                FranchiseAssignment.MANUAL_MEMBER,
                root
        );
    }

}
