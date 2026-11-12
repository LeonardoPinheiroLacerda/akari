package io.github.leonardopinheirolacerda.akari.domain.smartlist.service;

import io.github.leonardopinheirolacerda.akari.api.dto.AnimeBindingSummaryPage;
import io.github.leonardopinheirolacerda.akari.api.dto.SmartListRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.SmartListResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.SortDirection;
import io.github.leonardopinheirolacerda.akari.domain.anime.graph.FranchiseResolver;
import io.github.leonardopinheirolacerda.akari.domain.anime.graph.model.FranchiseResolution;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeOverrideService;
import io.github.leonardopinheirolacerda.akari.domain.smartlist.mapper.SmartListMapper;
import io.github.leonardopinheirolacerda.akari.domain.smartlist.model.SmartList;
import io.github.leonardopinheirolacerda.akari.domain.smartlist.model.SmartListCriteria;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Listas por critério: cadastro das listas e cálculo dos itens de cada uma.
 *
 * <p>Os itens são as raízes das franquias que passam no filtro, calculados em memória sobre a
 * coleção inteira (mesma abordagem do {@link FranchiseResolver}). Os critérios de franquia
 * (gêneros, tags, estúdios, status, adulto) trazem a raiz se algum membro casar todos eles; os
 * demais valem só pra raiz. Animes fora de qualquer franquia (ocultos) são ignorados.
 */
@ApplicationScoped
public class SmartListService {

    @Inject
    SmartListMapper smartListMapper;

    @Inject
    AnimeOverrideService animeOverrideService;

    /**
     * Lista as listas na ordem de exibição.
     *
     * @return as listas, por {@code position} e depois {@code id}
     */
    public List<SmartListResponse> listSmartLists() {
        final List<SmartList> smartLists = SmartList.listAllOrdered();
        return smartListMapper.toResponseList(smartLists);
    }

    /**
     * Cadastra uma lista.
     *
     * @param smartListRequest nome, posição, filtro e ordenação
     * @return a lista criada
     */
    @Transactional
    public SmartListResponse createSmartList(SmartListRequest smartListRequest) {
        final SmartList smartList = new SmartList();

        smartListMapper.updateFromRequest(smartListRequest, smartList);
        smartList.persist();

        return smartListMapper.toResponse(smartList);
    }

    /**
     * Busca uma lista.
     *
     * @param listId id da lista
     * @return a lista
     * @throws ResourceNotFoundException se a lista não existir
     */
    public SmartListResponse getSmartList(Integer listId) {
        final SmartList smartList = findOrThrow(listId);

        return smartListMapper.toResponse(smartList);
    }

    /**
     * Atualiza uma lista.
     *
     * @param listId id da lista
     * @param smartListRequest novo nome, posição, filtro e ordenação
     * @return a lista atualizada
     * @throws ResourceNotFoundException se a lista não existir
     */
    @Transactional
    public SmartListResponse updateSmartList(Integer listId, SmartListRequest smartListRequest) {
        final SmartList smartList = findOrThrow(listId);

        smartListMapper.updateFromRequest(smartListRequest, smartList);

        return smartListMapper.toResponse(smartList);
    }

    /**
     * Remove uma lista.
     *
     * @param listId id da lista
     * @throws ResourceNotFoundException se a lista não existir
     */
    @Transactional
    public void deleteSmartList(Integer listId) {
        final SmartList smartList = findOrThrow(listId);
        smartList.delete();
    }

    /**
     * Itens da lista: as raízes de franquia que passam no filtro, na ordenação da lista.
     *
     * @param listId id da lista
     * @param page página 0-based
     * @param size tamanho da página
     * @return a página de raízes
     * @throws ResourceNotFoundException se a lista não existir
     */
    public AnimeBindingSummaryPage listSmartListItems(Integer listId, Integer page, Integer size) {
        final SmartList smartList = findOrThrow(listId);

        final SmartListCriteria criteria = smartList.criteria;
        final FranchiseResolution resolution = FranchiseResolver.resolve();

        final Map<Integer, Anime> animeById = Anime
                .<Anime>listAll()
                .stream()
                .collect(Collectors.toMap(anime -> anime.anilistId, Function.identity()));

        final Set<Integer> rootIds = animeById.values()
                .stream()
                .filter(anime -> resolution.rootOf(anime.anilistId) != null)
                .filter(anime -> matchesFranchise(anime, criteria))
                .map(anime -> resolution.rootOf(anime.anilistId))
                .collect(Collectors.toSet());

        final List<Anime> roots = rootIds
                .stream()
                .map(animeById::get)
                .filter(root -> matchesRoot(root, criteria))
                .sorted(comparatorOf(smartList))
                .toList();

        final int from = Math.min(page * size, roots.size());
        final int to = Math.min(from + size, roots.size());

        return new AnimeBindingSummaryPage()
                .page(page)
                .size(size)
                .totalElements((long) roots.size())
                .totalPages((int) Math.ceil((double) roots.size() / size))
                .data(roots
                        .subList(from, to)
                        .stream()
                        .map(animeOverrideService::toBindingSummary)
                        .toList());
    }

    private SmartList findOrThrow(Integer listId) {
        return SmartList
                .find(listId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Não foi possível localizar uma lista com o id informado"
                ));
    }

    /**
     * {@code true} se o anime casa todos os critérios de franquia.
     */
    private static boolean matchesFranchise(Anime anime, SmartListCriteria criteria) {
        final List<String> genres = nullToEmpty(anime.genres);
        final List<String> tags = nullToEmpty(anime.tags)
                .stream()
                .map(tag -> tag.name)
                .toList();

        if (!containsAll(genres, criteria.includeGenres)) {
            return false;
        }
        if (!containsNone(genres, criteria.excludeGenres)) {
            return false;
        }
        if (!containsAll(tags, criteria.includeTags)) {
            return false;
        }
        if (!containsNone(tags, criteria.excludeTags)) {
            return false;
        }
        if (!accepts(criteria.studios, anime.studio)) {
            return false;
        }
        if (!accepts(criteria.statuses, anime.status)) {
            return false;
        }
        if (criteria.adult == null) {
            return true;
        }
        return criteria.adult == Boolean.TRUE.equals(anime.isAdult);
    }

    /**
     * {@code true} se a raiz casa todos os critérios de raiz.
     */
    private static boolean matchesRoot(Anime root, SmartListCriteria criteria) {
        if (!accepts(criteria.formats, root.format)) {
            return false;
        }
        if (!atLeast(root.seasonYear, criteria.yearFrom)) {
            return false;
        }
        if (!atMost(root.seasonYear, criteria.yearTo)) {
            return false;
        }
        if (!atLeast(root.score, criteria.minScore)) {
            return false;
        }
        if (!atLeast(root.meanScore, criteria.minMeanScore)) {
            return false;
        }
        return atLeast(root.popularity, criteria.minPopularity);
    }

    /**
     * Ordena pelo campo da lista, com valores ausentes no fim, e desempata pelo
     * {@code anilistId}.
     */
    private Comparator<Anime> comparatorOf(SmartList smartList) {
        final SortDirection direction = smartList.sortDirection;

        final Comparator<Anime> byField = switch (smartList.sortField) {
            case SCORE -> Comparator.comparing(
                    (Anime anime) -> anime.score,
                    order(direction)
            );
            case MEAN_SCORE -> Comparator.comparing(
                    (Anime anime) -> anime.meanScore,
                    order(direction)
            );
            case POPULARITY -> Comparator.comparing(
                    (Anime anime) -> anime.popularity,
                    order(direction)
            );
            case YEAR -> Comparator.comparing(
                    (Anime anime) -> anime.seasonYear,
                    order(direction)
            );
            case TITLE -> Comparator.comparing(
                    animeOverrideService::resolveTitle,
                    order(direction)
            );
        };

        return byField.thenComparing(anime -> anime.anilistId);
    }

    private static <T extends Comparable<T>> Comparator<T> order(SortDirection direction) {
        if (direction == SortDirection.ASC) {
            return Comparator.nullsLast(Comparator.naturalOrder());
        }
        return Comparator.nullsLast(Comparator.reverseOrder());
    }

    private static boolean containsAll(List<String> values, List<String> required) {
        if (required == null) {
            return true;
        }
        return values.containsAll(required);
    }

    private static boolean containsNone(List<String> values, List<String> forbidden) {
        if (forbidden == null) {
            return true;
        }
        return Collections.disjoint(values, forbidden);
    }

    private static boolean accepts(List<String> accepted, String value) {
        if (accepted == null) {
            return true;
        }
        if (accepted.isEmpty()) {
            return true;
        }
        return accepted.contains(value);
    }

    private static <T extends Comparable<T>> boolean atLeast(T value, T min) {
        if (min == null) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return value.compareTo(min) >= 0;
    }

    private static <T extends Comparable<T>> boolean atMost(T value, T max) {
        if (max == null) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return value.compareTo(max) <= 0;
    }

    private static <T> List<T> nullToEmpty(List<T> values) {
        if (values == null) {
            return List.of();
        }
        return values;
    }

}
