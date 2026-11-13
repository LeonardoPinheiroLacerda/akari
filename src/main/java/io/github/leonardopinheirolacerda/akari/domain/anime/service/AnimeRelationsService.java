package io.github.leonardopinheirolacerda.akari.domain.anime.service;

import io.github.leonardopinheirolacerda.akari.api.dto.AnimeFranchiseResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimePage;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeRelationGraphResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeRelationNode;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeResponse;
import io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos.AnilistMediaResponse;
import io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos.AnilistRelationEdgeResponse;
import io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos.AnilistRelationNodeResponse;
import io.github.leonardopinheirolacerda.akari.domain.anilist.service.AnilistSearchService;
import io.github.leonardopinheirolacerda.akari.domain.anime.graph.FranchiseClassifier;
import io.github.leonardopinheirolacerda.akari.domain.anime.graph.FranchiseResolver;
import io.github.leonardopinheirolacerda.akari.domain.anime.graph.RelationGraph;
import io.github.leonardopinheirolacerda.akari.domain.anime.graph.model.FranchiseResolution;
import io.github.leonardopinheirolacerda.akari.domain.anime.graph.model.FranchiseSections;
import io.github.leonardopinheirolacerda.akari.domain.anime.graph.model.RelationClosure;
import io.github.leonardopinheirolacerda.akari.domain.anime.mapper.AnimeRelationMapper;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.AnimeRelation;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.RelationType;
import io.github.leonardopinheirolacerda.akari.domain.user.service.UserService;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.github.leonardopinheirolacerda.akari.model.PageResult;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Grafo de relações entre animes (sequel/prequel/side story), a sincronização das arestas a
 * partir da resposta da AniList, as raízes das franquias e a franquia organizada pros
 * clientes.
 */
@ApplicationScoped
public class AnimeRelationsService {

    @Inject
    AnimeRelationMapper animeRelationMapper;

    @Inject
    AnimeOverrideService animeOverrideService;

    @Inject
    AnilistSearchService anilistSearchService;

    @Inject
    UserService userService;

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
     * Busca o id do prequel direto de um anime, se houver uma aresta {@code PREQUEL} registrada
     * saindo dele — usado pelo {@link AbsoluteEpisodeOffsetService}, que anda a cadeia de
     * prequels pra calcular o deslocamento de numeração absoluta no pairing de episódios.
     *
     * @param anilistId anime cujo prequel se procura
     * @return o anilistId do prequel direto, ou vazio se não houver
     */
    @Transactional
    public Optional<Integer> findDirectPrequelId(Integer anilistId) {
        return AnimeRelation.findPrequelEdge(anilistId)
                .map(edge -> edge.toAnilistId);
    }

    /**
     * Lista as raízes das franquias da coleção (naturais e manuais), paginadas — ver
     * {@link FranchiseResolver}.
     *
     * @param page página 0-based
     * @param size tamanho da página
     * @return a página de raízes
     */
    public AnimePage listRelationRoots(Integer page, Integer size) {
        final FranchiseResolution resolution = FranchiseResolver.resolve();
        final PageResult<Anime> roots = Anime.findPageByIds(resolution.rootIds(), page, size);

        final List<AnimeResponse> data = roots.data()
                .stream()
                .map(anime -> animeOverrideService.toResponse(anime, resolution))
                .toList();

        return new AnimePage()
                .page(roots.page())
                .size(roots.size())
                .totalElements(roots.totalElements())
                .totalPages(roots.totalPages())
                .data(data);
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
        requireAnimeExists(anilistId, "montar o grafo de relações");

        final RelationClosure closure = RelationGraph.closure(anilistId, depth);
        final List<AnimeRelation> edges = closure.edges();
        final FranchiseResolution resolution = FranchiseResolver.resolve();

        final Map<Integer, Anime> animeById = Anime.<Anime>findByIds(new ArrayList<>(closure.animeIds()))
                .stream()
                .collect(Collectors.toMap(anime -> anime.anilistId, Function.identity()));

        final List<AnimeRelationNode> nodes = closure.animeIds()
                .stream()
                .map(animeById::get)
                .filter(Objects::nonNull)
                .map(anime -> animeRelationMapper.toNode(
                        anime,
                        animeOverrideService.resolveTitle(anime),
                        animeOverrideService.resolveThumbnailMedium(anime),
                        resolution.rootKindOf(anime.anilistId)))
                .toList();

        return new AnimeRelationGraphResponse()
                .rootAnilistId(anilistId)
                .nodes(nodes)
                .edges(animeRelationMapper.toEdgeList(edges));
    }

    /**
     * Monta a franquia do anime informado já organizada em raiz/temporadas/filmes/especiais,
     * pra tela de detalhe do app — ver {@link FranchiseResolver} pra a que franquia cada anime
     * pertence e {@link FranchiseClassifier} pra como os membros são divididos.
     *
     * @param anilistId anime que ancora a busca; não precisa ser a raiz
     * @param userId usuário cuja lista define o {@code inWatchlist}; {@code null} deixa
     *               {@code inWatchlist} {@code null}
     * @return a franquia organizada
     * @throws ResourceNotFoundException se não há metadata salva pro id informado, se o anime
     *         está fora de qualquer franquia, ou se o {@code userId} informado não existir
     */
    public AnimeFranchiseResponse getAnimeFranchise(Integer anilistId, Integer userId) {
        requireAnimeExists(anilistId, "montar a franquia");

        final FranchiseResolution resolution = FranchiseResolver.resolve();
        final Integer rootId = resolution.rootOf(anilistId);

        if (rootId == null) {
            Log.warnf("Anime %d está fora de qualquer franquia", anilistId);
            throw new ResourceNotFoundException("O anime informado está fora de qualquer franquia");
        }

        final List<Integer> memberIds = resolution.membersOf(rootId);

        final Map<Integer, Anime> memberById = Anime.<Anime>findByIds(memberIds)
                .stream()
                .collect(Collectors.toMap(anime -> anime.anilistId, Function.identity()));

        final FranchiseSections sections = FranchiseClassifier.classify(
                rootId,
                memberById,
                AnimeRelation.findByAnilistIdsEitherSide(memberIds)
        );

        final Boolean inWatchlist = userId == null
                ? null
                : userService.isInWatchlist(userId, anilistId);

        return new AnimeFranchiseResponse()
                .root(animeOverrideService.toResponse(memberById.get(rootId), resolution))
                .seasons(toResponses(sections.seasonIds(), memberById, resolution))
                .movies(toResponses(sections.movieIds(), memberById, resolution))
                .specials(toResponses(sections.specialIds(), memberById, resolution))
                .inWatchlist(inWatchlist);
    }

    private List<AnimeResponse> toResponses(List<Integer> anilistIds,
                                            Map<Integer, Anime> animeById,
                                            FranchiseResolution resolution) {

        return anilistIds.stream()
                .map(id -> animeOverrideService.toResponse(animeById.get(id), resolution))
                .toList();
    }

    private void syncRelations(AnilistMediaResponse anilist, Set<Integer> visited) {
        final Integer anilistId = anilist.id();

        if (anilistId == null || !visited.add(anilistId)) {
            return;
        }

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
        relation.relationType = toRelationType(edge.relationType());
        relation.persist();

        if (!visited.contains(node.id())) {
            final AnilistMediaResponse neighbor = anilistSearchService.findById(node.id(), false);
            syncRelations(neighbor, visited);
        }
    }

    /**
     * Garante que o anime existe antes de montar grafo/franquia a partir dele.
     *
     * @param anilistId anime a verificar
     * @param context operação que motivou a busca, só pro log (ex.: {@code "montar a franquia"})
     * @throws ResourceNotFoundException se não há metadata salva pro id informado
     */
    private static void requireAnimeExists(Integer anilistId, String context) {
        if (Anime.find(anilistId).isEmpty()) {
            Log.warnf("Anime %d não encontrado pra %s", anilistId, context);
            throw new ResourceNotFoundException(
                    "Não foi possível localizar a metadata de um anime com o id informado");
        }
    }

    /**
     * Converte o tipo bruto de relação da AniList pro enum interno. Tipos sem relevância pro
     * grafo caem em {@link RelationType#OTHER}; valores desconhecidos em
     * {@link RelationType#UNKNOWN} — curadoria de quais categorias da AniList importam pro
     * grafo de franquia, por isso vive aqui e não no mapper.
     *
     * @param rawRelationType valor bruto (ex.: {@code SEQUEL}, {@code ADAPTATION})
     * @return o {@link RelationType} correspondente
     */
    private static RelationType toRelationType(String rawRelationType) {
        if (rawRelationType == null) {
            return RelationType.UNKNOWN;
        }

        return switch (rawRelationType) {
            case "SEQUEL" -> RelationType.SEQUEL;
            case "PREQUEL" -> RelationType.PREQUEL;
            case "SIDE_STORY" -> RelationType.SIDE_STORY;
            case "PARENT" -> RelationType.PARENT;
            case "SUMMARY" -> RelationType.SUMMARY;
            case "ALTERNATIVE" -> RelationType.ALTERNATIVE;
            case "SPIN_OFF" -> RelationType.SPIN_OFF;
            case "OTHER", "ADAPTATION", "CHARACTER", "CONTAINS", "COMPILATION", "SOURCE" -> RelationType.OTHER;
            default -> RelationType.UNKNOWN;
        };
    }

}
