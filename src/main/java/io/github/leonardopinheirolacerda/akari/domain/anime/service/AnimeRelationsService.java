package io.github.leonardopinheirolacerda.akari.domain.anime.service;

import io.github.leonardopinheirolacerda.akari.api.dto.AnimePage;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeRelationEdge;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeRelationGraphResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeRelationNode;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeRootKind;
import io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos.AnilistMediaResponse;
import io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos.AnilistRelationEdgeResponse;
import io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos.AnilistRelationNodeResponse;
import io.github.leonardopinheirolacerda.akari.domain.anilist.service.AnilistSearchService;
import io.github.leonardopinheirolacerda.akari.domain.anime.mapper.AnimeMapper;
import io.github.leonardopinheirolacerda.akari.domain.anime.mapper.AnimeRelationMapper;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.AnimeRelation;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.RelationType;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.github.leonardopinheirolacerda.akari.model.PageResult;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Grafo de relações entre animes (sequel/prequel/side story) e a sincronização das arestas a
 * partir da resposta da AniList.
 */
@ApplicationScoped
public class AnimeRelationsService {

    @Inject
    AnimeMapper animeMapper;

    @Inject
    AnimeRelationMapper animeRelationMapper;

    @Inject
    AnilistSearchService anilistSearchService;

    /**
     * Reconstrói as arestas que saem do anime a partir da resposta da AniList, em cascata:
     * sempre que uma relação aponta pra outro anime já conhecido localmente, a aresta é salva
     * e esse vizinho tem suas próprias arestas recalculadas também — garante que os dois lados
     * do grafo reflitam a mesma estrutura, mesmo que o vizinho tenha sido sincronizado antes do
     * anime atual existir. Relações pra animes ainda não vinculados localmente são ignoradas; o
     * grafo se fecha quando esses animes forem vinculados no futuro.
     *
     * @param anilist resposta da AniList do anime recém vinculado/sincronizado
     */
    @Transactional
    public void syncRelations(AnilistMediaResponse anilist) {
        syncRelations(anilist, new HashSet<>());
    }

    /**
     * Lista os animes-raiz da coleção, paginados.
     *
     * @param page página 0-based
     * @param size tamanho da página
     * @return a página de animes-raiz
     */
    public AnimePage listRelationRoots(Integer page, Integer size) {
        final PageResult<Anime> roots = Anime.findRoots(page, size);
        return animeMapper.toAnimePage(roots);
    }

    /**
     * Materializa o grafo de relações a partir do anime informado, via BFS bidirecional até
     * {@code depth} saltos.
     *
     * @param anilistId anime que ancora a busca
     * @param depth profundidade máxima da BFS
     * @return o grafo materializado
     * @throws ResourceNotFoundException se não há metadata salva pro id informado
     */
    public AnimeRelationGraphResponse getAnimeRelationGraph(Integer anilistId, Integer depth) {
        if (Anime.find(anilistId).isEmpty()) {
            Log.warnf("Anime %d não encontrado pra montar o grafo de relações", anilistId);
            throw new ResourceNotFoundException(
                    "Não foi possível localizar a metadata de um anime com o id informado");
        }

        final Set<Integer> visited = new LinkedHashSet<>();
        final Map<Integer, AnimeRelation> edgesById = new LinkedHashMap<>();
        visited.add(anilistId);

        List<Integer> currentLevel = List.of(anilistId);

        for (int level = 0; level < depth; level++) {
            final List<Integer> nextLevel = new ArrayList<>();

            for (Integer currentId : currentLevel) {
                expandNeighbors(currentId, visited, nextLevel, edgesById);
            }

            if (nextLevel.isEmpty()) {
                break;
            }

            currentLevel = nextLevel;
        }

        final List<Anime> animes = visited.stream()
                .map(Anime::find)
                .flatMap(Optional::stream)
                .toList();

        final List<AnimeRelation> edges = new ArrayList<>(edgesById.values());

        final List<AnimeRelationNode> nodes = animes.stream()
                .map(anime -> animeRelationMapper.toNode(
                        anime,
                        animeMapper.toDisplayTitle(anime),
                        animeMapper.toDisplayThumbnail(anime),
                        rootKindOf(anime, edges)))
                .toList();

        final List<AnimeRelationEdge> edgeList = animeRelationMapper.toEdgeList(edges);

        return new AnimeRelationGraphResponse()
                .rootAnilistId(anilistId)
                .nodes(nodes)
                .edges(edgeList);
    }

    private void syncRelations(AnilistMediaResponse anilist, Set<Integer> visited) {
        final Integer anilistId = anilist.id();

        if (anilistId == null || visited.contains(anilistId)) {
            return;
        }

        visited.add(anilistId);

        AnimeRelation.deleteByFromAnilistId(anilistId);

        if (anilist.relations() == null || anilist.relations().edges() == null) {
            return;
        }

        for (AnilistRelationEdgeResponse edge : anilist.relations().edges()) {
            saveEdgeAndFollow(anilistId, edge, visited);
        }
    }

    private void saveEdgeAndFollow(Integer fromAnilistId, AnilistRelationEdgeResponse edge, Set<Integer> visited) {
        final AnilistRelationNodeResponse node = edge.node();

        if (node == null || node.id() == null || !"ANIME".equals(node.type())) {
            return;
        }

        if (Anime.find(node.id()).isEmpty()) {
            return;
        }

        final AnimeRelation relation = new AnimeRelation();
        relation.fromAnilistId = fromAnilistId;
        relation.toAnilistId = node.id();
        relation.relationType = animeRelationMapper.toRelationType(edge.relationType());
        relation.persist();

        if (!visited.contains(node.id())) {
            final AnilistMediaResponse neighbor = anilistSearchService.findById(node.id(), false);
            syncRelations(neighbor, visited);
        }
    }

    private void expandNeighbors(
            Integer currentId,
            Set<Integer> visited,
            List<Integer> nextLevel,
            Map<Integer, AnimeRelation> edgesById) {

        for (AnimeRelation edge : AnimeRelation.findByAnilistIdEitherSide(currentId)) {
            edgesById.put(edge.id, edge);

            final Integer neighborId = otherSideOf(edge, currentId);

            if (visited.add(neighborId)) {
                nextLevel.add(neighborId);
            }
        }
    }

    private static Integer otherSideOf(AnimeRelation edge, Integer pivot) {
        if (edge.fromAnilistId.equals(pivot)) {
            return edge.toAnilistId;
        }
        return edge.fromAnilistId;
    }

    private static AnimeRootKind rootKindOf(Anime anime, List<AnimeRelation> edges) {
        return switch (anime.franchiseRootCuration) {
            case MANUAL_ROOT -> AnimeRootKind.MANUAL;
            case MANUAL_NOT_ROOT -> AnimeRootKind.NOT_ROOT;
            case AUTO -> hasAncestorLink(anime.anilistId, edges) ? AnimeRootKind.NOT_ROOT : AnimeRootKind.NATURAL;
        };
    }

    private static boolean hasAncestorLink(Integer anilistId, List<AnimeRelation> edges) {
        return edges.stream()
                .anyMatch(edge ->
                        edge.fromAnilistId.equals(anilistId)
                                && RelationType.ANCESTOR_LINKS.contains(edge.relationType));
    }

}
