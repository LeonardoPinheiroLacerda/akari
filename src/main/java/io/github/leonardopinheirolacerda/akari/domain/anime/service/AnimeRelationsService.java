package io.github.leonardopinheirolacerda.akari.domain.anime.service;

import io.github.leonardopinheirolacerda.akari.api.dto.AnimeFranchiseResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimePage;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeRelationEdge;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeRelationGraphResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeRelationNode;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeRootKind;
import io.github.leonardopinheirolacerda.akari.api.dto.FranchiseRootCuration;
import io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos.AnilistMediaResponse;
import io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos.AnilistRelationEdgeResponse;
import io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos.AnilistRelationNodeResponse;
import io.github.leonardopinheirolacerda.akari.domain.anilist.service.AnilistSearchService;
import io.github.leonardopinheirolacerda.akari.domain.anime.mapper.AnimeMapper;
import io.github.leonardopinheirolacerda.akari.domain.anime.mapper.AnimeRelationMapper;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.AnimeRelation;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.RelationType;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Season;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.github.leonardopinheirolacerda.akari.model.PageResult;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Grafo de relações entre animes (sequel/prequel/side story) e a sincronização das arestas a
 * partir da resposta da AniList.
 */
@ApplicationScoped
public class AnimeRelationsService {

    /**
     * Ordena {@code movies}/{@code specials} (antes do split por formato) por {@code
     * seasonYear}, depois pela ordem natural de {@link Season}, com {@code anilistId} como
     * desempate final — sem uma data de estreia mais precisa persistida localmente (ver
     * {@link Anime#seasonYear}/{@link Anime#season}).
     */
    private static final Comparator<Anime> MOVIES_AND_SPECIALS_ORDER = Comparator
            .comparing((Anime anime) -> anime.seasonYear, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(AnimeRelationsService::seasonOf, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(anime -> anime.anilistId);

    @Inject
    AnimeMapper animeMapper;

    @Inject
    AnimeRelationMapper animeRelationMapper;

    @Inject
    AnimeOverrideService animeOverrideService;

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
     * Busca o id do prequel direto de um anime, se houver uma aresta {@code PREQUEL} registrada
     * saindo dele — usado por quem precisa andar a cadeia de prequels (ex.: {@code ingestion},
     * pra calcular deslocamento de numeração absoluta), sem acessar {@link AnimeRelation}
     * diretamente.
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
     * Lista os animes-raiz da coleção, paginados.
     *
     * @param page página 0-based
     * @param size tamanho da página
     * @return a página de animes-raiz
     */
    public AnimePage listRelationRoots(Integer page, Integer size) {
        final PageResult<Anime> roots = Anime.findRoots(page, size);

        final List<AnimeResponse> data = roots.data()
                .stream()
                .map(this::toResponse)
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

        final Map<Integer, AnimeRelation> edgesById = new LinkedHashMap<>();
        final Set<Integer> visited = bfsClosure(anilistId, depth, type -> true, edgesById);

        final List<Anime> animes = visited.stream()
                .map(Anime::find)
                .flatMap(Optional::stream)
                .toList();

        final List<AnimeRelation> edges = new ArrayList<>(edgesById.values());

        final List<AnimeRelationNode> nodes = animes.stream()
                .map(anime -> animeRelationMapper.toNode(
                        anime,
                        animeOverrideService.resolveTitle(anime),
                        animeOverrideService.resolveThumbnailMedium(anime),
                        rootKindOf(anime, edges)))
                .toList();

        final List<AnimeRelationEdge> edgeList = animeRelationMapper.toEdgeList(edges);

        return new AnimeRelationGraphResponse()
                .rootAnilistId(anilistId)
                .nodes(nodes)
                .edges(edgeList);
    }

    /**
     * Monta a franquia do anime informado já organizada em raiz/temporadas/filmes/especiais,
     * pra tela de detalhe do app — ver {@link #findFranchiseRootId} pra como a raiz é
     * encontrada, {@link RelationType#FRANCHISE_LINKS} pra quais arestas formam a franquia a
     * partir dela, e {@link RelationType#SERIES_FORMATS}/{@link RelationType#MOVIE_FORMATS}/
     * {@link RelationType#SPECIAL_FORMATS} (as três disjuntas) pra como a cadeia de
     * {@code SEQUEL} é dividida entre temporada, filme e especial.
     *
     * @param anilistId anime que ancora a busca; não precisa ser a raiz
     * @return a franquia organizada
     * @throws ResourceNotFoundException se não há metadata salva pro id informado
     */
    public AnimeFranchiseResponse getAnimeFranchise(Integer anilistId) {
        requireAnimeExists(anilistId, "montar a franquia");

        final Integer rootId = findFranchiseRootId(anilistId);

        final Map<Integer, AnimeRelation> edgesById = new LinkedHashMap<>();
        final Set<Integer> franchiseIds = bfsClosure(rootId, Integer.MAX_VALUE, RelationType.FRANCHISE_LINKS::contains, edgesById);

        final List<Integer> sequelChain = sequelChainFrom(rootId, edgesById);

        final Map<Integer, Anime> animeById = Anime.<Anime>findByIds(new ArrayList<>(franchiseIds))
                .stream()
                .collect(Collectors.toMap(anime -> anime.anilistId, Function.identity()));

        final Set<Integer> seasonIds = new LinkedHashSet<>();
        final Set<Integer> excludedIds = new HashSet<>();

        for (Integer id : sequelChain) {
            final String format = animeById.get(id).format;

            if (RelationType.SERIES_FORMATS.contains(format)) {
                seasonIds.add(id);
            } else if (!RelationType.MOVIE_FORMATS.contains(format)
                    && !RelationType.SPECIAL_FORMATS.contains(format)
                    && !hasDirectSpecialLink(id, edgesById)) {
                excludedIds.add(id);
            }
        }

        final List<AnimeResponse> seasons = sequelChain.stream()
                .filter(seasonIds::contains)
                .map(id -> toResponse(animeById.get(id)))
                .toList();

        final List<Anime> specialsAndMovies = franchiseIds.stream()
                .filter(id -> !seasonIds.contains(id) && !excludedIds.contains(id))
                .map(animeById::get)
                .sorted(MOVIES_AND_SPECIALS_ORDER)
                .toList();

        final List<AnimeResponse> movies = specialsAndMovies.stream()
                .filter(anime -> RelationType.MOVIE_FORMATS.contains(anime.format))
                .map(this::toResponse)
                .toList();

        final List<AnimeResponse> specials = specialsAndMovies.stream()
                .filter(anime -> !RelationType.MOVIE_FORMATS.contains(anime.format))
                .map(this::toResponse)
                .toList();

        return new AnimeFranchiseResponse()
                .root(toResponse(animeById.get(rootId)))
                .seasons(seasons)
                .movies(movies)
                .specials(specials);
    }

    /**
     * Sobe a cadeia de ancestrais a partir do anime informado até achar a raiz da franquia — a
     * mesma regra de {@code Anime#findRoots}: para numa curadoria {@code MANUAL_ROOT} mesmo que
     * ela ainda tenha uma aresta de ancestral, ou quando não sobra mais nenhuma pra subir. Em
     * caso de mais de uma aresta de ancestral saindo do mesmo anime, desempata pelo menor
     * {@code anilistId}, pra subida determinística — mesmo critério de {@link #nextSequelId}.
     *
     * <p>Se a subida acabar formando um ciclo (ex.: dois animes se referenciando como
     * ancestrais um do outro), devolve o último pivot processado com sucesso antes do ciclo,
     * não o nó que causou a repetição — evita que a ordem de descoberta decida a raiz
     * arbitrariamente.
     *
     * @param anilistId anime de onde a subida começa
     * @return o {@code anilistId} da raiz
     * @see #isFranchiseAncestorEdge
     */
    private static Integer findFranchiseRootId(Integer anilistId) {
        final Set<Integer> visited = new HashSet<>();

        Integer current = anilistId;
        Integer lastPivot = anilistId;

        while (visited.add(current)) {
            final Integer pivot = current;
            lastPivot = pivot;
            final Anime anime = Anime.find(pivot).orElseThrow();

            if (anime.franchiseRootCuration == FranchiseRootCuration.MANUAL_ROOT) {
                return pivot;
            }

            final Optional<Integer> ancestorId = AnimeRelation.findByAnilistIdEitherSide(pivot)
                    .stream()
                    .filter(edge -> isFranchiseAncestorEdge(edge, pivot))
                    .map(edge -> otherSideOf(edge, pivot))
                    .min(Comparator.naturalOrder());

            if (ancestorId.isEmpty()) {
                if (anime.franchiseRootCuration == FranchiseRootCuration.MANUAL_NOT_ROOT) {
                    Log.warnf(
                            "Anime %d está curado como MANUAL_NOT_ROOT mas não tem aresta de "
                                    + "ancestral pra subir; usando como raiz da franquia por falta de "
                                    + "alternativa",
                            pivot);
                }
                return pivot;
            }

            current = ancestorId.get();
        }

        return lastPivot;
    }

    /**
     * {@code true} se a aresta indicar que {@code pivot} é derivado/descende do outro lado,
     * pra subida de {@link #findFranchiseRootId}. Saindo de {@code pivot}, vale pra qualquer
     * tipo de {@link RelationType#ANCESTOR_LINKS}; chegando em {@code pivot}, só pra
     * {@code SUMMARY} — essa é a única direção invertida (ver javadoc da constante).
     * {@code ALTERNATIVE} não entra em nenhuma direção: a AniList tagueia esse tipo de forma
     * simétrica com frequência, o que criaria um ciclo sem um lado objetivamente ancestral.
     *
     * @param edge aresta a avaliar
     * @param pivot anime cuja ancestralidade se verifica
     * @return {@code true} se a aresta aponta pra um ancestral de {@code pivot}
     */
    private static boolean isFranchiseAncestorEdge(AnimeRelation edge, Integer pivot) {
        if (edge.fromAnilistId.equals(pivot)) {
            return RelationType.ANCESTOR_LINKS.contains(edge.relationType);
        }
        return edge.relationType == RelationType.SUMMARY;
    }

    /**
     * Segue a cadeia de arestas {@code SEQUEL} a partir da raiz, uma de cada vez; em caso de
     * ramificação (mais de uma aresta {@code SEQUEL} saindo do mesmo anime) desempata pelo
     * menor {@code anilistId}, pra ordem determinística.
     *
     * @param rootId raiz da franquia, primeiro elemento da cadeia
     * @param edgesById arestas já coletadas por {@link #bfsClosure}
     * @return a cadeia, na ordem de sequência, com a raiz como primeiro elemento
     */
    private static List<Integer> sequelChainFrom(Integer rootId, Map<Integer, AnimeRelation> edgesById) {
        final List<Integer> chain = new ArrayList<>();
        final Set<Integer> visited = new HashSet<>();

        Integer current = rootId;

        while (current != null && visited.add(current)) {
            chain.add(current);
            current = nextSequelId(current, edgesById);
        }

        return chain;
    }

    private static Integer nextSequelId(Integer fromId, Map<Integer, AnimeRelation> edgesById) {
        return edgesById.values()
                .stream()
                .filter(edge -> edge.relationType == RelationType.SEQUEL && edge.fromAnilistId.equals(fromId))
                .map(edge -> edge.toAnilistId)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    /**
     * {@code true} se o anime tiver, em qualquer ponta, uma aresta de {@link
     * RelationType#SPECIAL_LINKS} — usado pra não excluir da franquia um anime da cadeia de
     * {@code SEQUEL} com formato desconhecido quando ele também está ligado como especial por
     * outro caminho (ex.: {@code SIDE_STORY}).
     *
     * @param id anime a verificar
     * @param edgesById arestas já coletadas por {@link #bfsClosure}
     * @return {@code true} se houver pelo menos uma aresta de {@code SPECIAL_LINKS} tocando o id
     */
    private static boolean hasDirectSpecialLink(Integer id, Map<Integer, AnimeRelation> edgesById) {
        return edgesById.values()
                .stream()
                .anyMatch(edge ->
                        (edge.fromAnilistId.equals(id) || edge.toAnilistId.equals(id))
                                && RelationType.SPECIAL_LINKS.contains(edge.relationType));
    }

    private AnimeResponse toResponse(Anime anime) {
        return animeOverrideService.applyTo(animeMapper.toView(anime), anime);
    }

    /**
     * Converte {@code Anime#season} (string crua da AniList) pro {@link Season}, pra ordenar
     * {@link #MOVIES_AND_SPECIALS_ORDER} pela ordem natural do enum em vez de comparar strings.
     *
     * @param anime anime cujo {@code season} se converte
     * @return o {@link Season} correspondente; {@code null} se for {@code null} ou não bater
     *         com nenhuma constante conhecida
     */
    private static Season seasonOf(Anime anime) {
        if (anime.season == null) {
            return null;
        }
        try {
            return Season.valueOf(anime.season);
        } catch (IllegalArgumentException e) {
            return null;
        }
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
        relation.relationType = toRelationType(edge.relationType());
        relation.persist();

        if (!visited.contains(node.id())) {
            final AnilistMediaResponse neighbor = anilistSearchService.findById(node.id(), false);
            syncRelations(neighbor, visited);
        }
    }

    /**
     * BFS bidirecional a partir de {@code startId}, até {@code maxDepth} saltos (use
     * {@code Integer.MAX_VALUE} pra fechar sem limite), só pelas arestas que casam
     * {@code edgeFilter} — base de {@link #getAnimeRelationGraph} (sem filtro de tipo, com
     * {@code depth}) e {@link #getAnimeFranchise} (filtrado por {@link
     * RelationType#FRANCHISE_LINKS}, sem limite).
     *
     * @param startId anime que ancora a busca
     * @param maxDepth profundidade máxima da BFS
     * @param edgeFilter tipos de relação que contam como aresta do fechamento
     * @param edgesById preenchido com as arestas percorridas que passaram o filtro
     * @return os {@code anilistId} alcançados, incluindo o próprio {@code startId}
     */
    private Set<Integer> bfsClosure(
            Integer startId,
            Integer maxDepth,
            Predicate<RelationType> edgeFilter,
            Map<Integer, AnimeRelation> edgesById) {

        final Set<Integer> visited = new LinkedHashSet<>();
        visited.add(startId);

        List<Integer> frontier = List.of(startId);

        for (int level = 0; !frontier.isEmpty() && level < maxDepth; level++) {
            final List<Integer> nextFrontier = new ArrayList<>();

            for (Integer currentId : frontier) {
                for (AnimeRelation edge : AnimeRelation.findByAnilistIdEitherSide(currentId)) {
                    if (!edgeFilter.test(edge.relationType)) {
                        continue;
                    }

                    edgesById.put(edge.id, edge);

                    final Integer neighborId = otherSideOf(edge, currentId);

                    if (visited.add(neighborId)) {
                        nextFrontier.add(neighborId);
                    }
                }
            }

            frontier = nextFrontier;
        }

        return visited;
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
