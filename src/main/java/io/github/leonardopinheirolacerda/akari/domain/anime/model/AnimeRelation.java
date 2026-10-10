package io.github.leonardopinheirolacerda.akari.domain.anime.model;

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
 * Aresta direcionada do grafo de relações entre animes — "{@code fromAnilistId} tem uma
 * {@code relationType} com {@code toAnilistId}". PK surrogate simplifica o mapping; a
 * idempotência do sync fica na UNIQUE composta da migration.
 */
@Entity
@Table(name = "anime_relation")
public class AnimeRelation extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;

    @Column(name = "from_anilist_id")
    public Integer fromAnilistId;

    @Column(name = "to_anilist_id")
    public Integer toAnilistId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type")
    public RelationType relationType;

    /**
     * Arestas onde {@code anilistId} aparece em qualquer ponta — usado pela BFS bidirecional
     * do grafo de relações.
     *
     * @param anilistId anime pivô
     * @return arestas com {@code fromAnilistId} ou {@code toAnilistId} igual ao pivô
     */
    public static List<AnimeRelation> findByAnilistIdEitherSide(Integer anilistId) {
        return AnimeRelation
                .find("fromAnilistId = ?1 or toAnilistId = ?1", anilistId)
                .list();
    }

    /**
     * Remove todas as arestas cujo {@code fromAnilistId} é o informado — usado pra limpar
     * antes de re-persistir no sync, evitando duplicatas e refletindo relações removidas
     * upstream.
     *
     * @param anilistId origem
     */
    public static void deleteByFromAnilistId(Integer anilistId) {
        AnimeRelation.delete("fromAnilistId", anilistId);
    }

}
