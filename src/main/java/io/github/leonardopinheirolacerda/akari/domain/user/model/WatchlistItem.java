package io.github.leonardopinheirolacerda.akari.domain.user.model;

import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.model.PageResult;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Título (anime) na lista de um usuário.
 */
@Entity
@Table(name = "user_watchlist_item")
public class WatchlistItem extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    public User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "anilist_id")
    public Anime anime;

    @CreationTimestamp
    @Column(name = "added_at", updatable = false)
    public OffsetDateTime addedAt;

    /**
     * Busca um título na lista de um usuário.
     *
     * @param userId id do usuário
     * @param anilistId id do anime na AniList
     * @return o item, se o título já estiver na lista
     */
    public static Optional<WatchlistItem> findByUserAndAnime(Integer userId, Integer anilistId) {
        return WatchlistItem
                .find("user.id = ?1 and anime.anilistId = ?2", userId, anilistId)
                .firstResultOptional();
    }

    /**
     * Pagina a lista de um usuário, do título adicionado mais recentemente pro mais antigo.
     *
     * @param userId id do usuário
     * @param page página desejada, 0-based
     * @param size tamanho da página
     * @return página da lista; vazia se o usuário não adicionou nenhum título
     */
    public static PageResult<WatchlistItem> findPageByUser(Integer userId, Integer page, Integer size) {
        final PanacheQuery<WatchlistItem> panacheQuery = WatchlistItem
                .find("user.id = ?1 order by addedAt desc, id desc", userId)
                .page(page, size);

        return new PageResult<>(
                panacheQuery.list(),
                page,
                size,
                panacheQuery.count(),
                panacheQuery.pageCount()
        );
    }

}
