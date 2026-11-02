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
import io.github.leonardopinheirolacerda.akari.domain.anime.graph.RelationGraph;
import io.github.leonardopinheirolacerda.akari.domain.anime.graph.model.RelationClosure;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
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

        final RelationClosure closure = RelationGraph.closure(anilistId, depth, type -> true);
        final List<AnimeRelation> edges = closure.edges();

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
                        RelationGraph.rootKindOf(anime, edges)))
                .toList();

        return new AnimeRelationGraphResponse()
                .rootAnilistId(anilistId)
                .nodes(nodes)
                .edges(animeRelationMapper.toEdgeList(edges));
    }

    /**
     * Monta a franquia do anime informado já organizada em raiz/temporadas/filmes/especiais,
     * pra tela de detalhe do app — ver {@link RelationGraph#findFranchiseRootId} pra como a raiz é
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

        final Integer rootId = RelationGraph.findFranchiseRootId(anilistId);

        final RelationClosure closure = RelationGraph.closure(rootId, Integer.MAX_VALUE, RelationType.FRANCHISE_LINKS::contains);
        final Set<Integer> franchiseIds = closure.animeIds();
        final List<AnimeRelation> edges = closure.edges();

        final List<Integer> sequelChain = RelationGraph.sequelChainFrom(rootId, edges);

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
                    && !RelationGraph.hasDirectSpecialLink(id, edges)) {
                excludedIds.add(id);
            }
        }

        final List<AnimeResponse> seasons = seasonIds.stream()
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
