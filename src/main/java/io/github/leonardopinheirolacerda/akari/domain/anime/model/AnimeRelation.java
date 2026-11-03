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
import java.util.Optional;

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
     * Arestas onde qualquer um dos {@code anilistIds} aparece em qualquer ponta — usado pela BFS
     * bidirecional do grafo de relações, uma consulta por nível.
     *
     * @param anilistIds animes da fronteira da BFS
     * @return arestas com {@code fromAnilistId} ou {@code toAnilistId} entre os informados
     */
    public static List<AnimeRelation> findByAnilistIdsEitherSide(List<Integer> anilistIds) {
        return AnimeRelation
                .find("fromAnilistId in ?1 or toAnilistId in ?1", anilistIds)
                .list();
    }

    /**
     * Todas as arestas dos tipos informados — usado pela resolução de franquias, que calcula o
     * pai automático de todos os animes de uma vez.
     *
     * @param relationTypes tipos de relação
     * @return as arestas desses tipos
     */
    public static List<AnimeRelation> findByTypes(List<RelationType> relationTypes) {
        return AnimeRelation
                .find("relationType in ?1", relationTypes)
                .list();
    }

    /**
     * Busca a aresta de prequel direto que sai do anime informado — usado pra andar a cadeia de
     * numeração absoluta (cada temporada aponta pra sua antecessora direta via {@code PREQUEL}).
     *
     * @param anilistId anime cujo prequel se procura
     * @return a aresta, se esse anime tiver um prequel registrado
     */
    public static Optional<AnimeRelation> findPrequelEdge(Integer anilistId) {
        return AnimeRelation
                .find("fromAnilistId = ?1 and relationType = ?2", anilistId, RelationType.PREQUEL)
                .firstResultOptional();
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
