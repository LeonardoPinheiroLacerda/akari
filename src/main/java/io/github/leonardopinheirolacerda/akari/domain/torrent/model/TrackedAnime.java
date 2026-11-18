package io.github.leonardopinheirolacerda.akari.domain.torrent.model;

import io.github.leonardopinheirolacerda.akari.api.dto.TorrentProviderType;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Anime rastreado numa edição de um provedor de torrent: os episódios novos da edição são
 * baixados sozinhos.
 *
 * <p>Guarda o {@code anilistId} solto, sem referência a {@code Anime}: o anime só ganha linha no
 * banco quando é vinculado a uma pasta, no primeiro download. Pelo mesmo motivo, título e capa
 * vêm da AniList e ficam guardados aqui.
 */
@Entity
@Table(name = "tracked_anime")
public class TrackedAnime extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;

    @Column(name = "anilist_id", nullable = false, unique = true)
    public Integer anilistId;

    @Column(nullable = false)
    public String title;

    @Column(name = "cover_image")
    public String coverImage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public TorrentProviderType provider;

    /**
     * Queries de busca no provedor; as releases de todas são juntadas.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    public List<String> queries;

    @Column(name = "edition_key", nullable = false)
    public String editionKey;

    @Column(name = "edition_group")
    public String editionGroup;

    @Column(name = "edition_label", nullable = false)
    public String editionLabel;

    /**
     * Maior episódio da edição já encontrado no provedor; episódios acima dele são novos.
     */
    @Column(name = "latest_episode")
    public Integer latestEpisode;

    @Column(name = "last_checked_at")
    public OffsetDateTime lastCheckedAt;

    @Column(name = "last_error")
    public String lastError;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public OffsetDateTime createdAt;

    /**
     * Busca o rastreio de um anime.
     *
     * @param anilistId id do anime na AniList
     * @return o rastreio, se o anime já é rastreado
     */
    public static Optional<TrackedAnime> findByAnilistId(Integer anilistId) {
        return TrackedAnime
                .find("anilistId", anilistId)
                .firstResultOptional();
    }

    /**
     * Lista os rastreios, do mais recente para o mais antigo.
     *
     * @return todos os rastreios
     */
    public static List<TrackedAnime> listNewestFirst() {
        return TrackedAnime.list("order by createdAt desc, id desc");
    }
}
