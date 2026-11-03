package io.github.leonardopinheirolacerda.akari.domain.anime.service;

import io.github.leonardopinheirolacerda.akari.api.dto.AnimeBindingSummaryPage;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeFranchiseAssignmentRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.CreateFranchiseRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.FranchiseAssignment;
import io.github.leonardopinheirolacerda.akari.api.dto.FranchiseDetail;
import io.github.leonardopinheirolacerda.akari.api.dto.FranchiseKind;
import io.github.leonardopinheirolacerda.akari.api.dto.FranchiseMember;
import io.github.leonardopinheirolacerda.akari.api.dto.FranchiseRelatedAnime;
import io.github.leonardopinheirolacerda.akari.api.dto.FranchiseSummary;
import io.github.leonardopinheirolacerda.akari.api.dto.FranchiseSummaryPage;
import io.github.leonardopinheirolacerda.akari.domain.anime.graph.FranchiseClassifier;
import io.github.leonardopinheirolacerda.akari.domain.anime.graph.FranchiseResolver;
import io.github.leonardopinheirolacerda.akari.domain.anime.graph.model.FranchiseResolution;
import io.github.leonardopinheirolacerda.akari.domain.anime.graph.model.FranchiseSections;
import io.github.leonardopinheirolacerda.akari.domain.anime.mapper.AnimeRelationMapper;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.AnimeRelation;
import io.github.leonardopinheirolacerda.akari.exceptions.BusinessRuleException;
import io.github.leonardopinheirolacerda.akari.exceptions.ConflictException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.github.leonardopinheirolacerda.akari.model.PageResult;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Organização administrativa das franquias (portal): listar, criar e remover franquias manuais,
 * mover um anime de franquia ou ocultá-lo. A que franquia cada anime pertence é sempre
 * recalculada pelo {@link FranchiseResolver}; aqui só se grava a atribuição e as raízes manuais.
 */
@ApplicationScoped
public class AnimeFranchiseService {

    @Inject
    AnimeService animeService;

    @Inject
    AnimeRelationMapper animeRelationMapper;

    @Inject
    AnimeOverrideService animeOverrideService;

    /**
     * Lista as franquias da coleção, paginadas por {@code anilistId} da raiz.
     *
     * @param q texto buscado no título da raiz e dos membros; {@code null} ou vazio não filtra
     * @param kind tipo de raiz; {@code null} não filtra
     * @param page página 0-based
     * @param size tamanho da página
     * @return a página de franquias
     */
    public FranchiseSummaryPage listFranchises(String q, FranchiseKind kind, Integer page, Integer size) {
        final FranchiseResolution resolution = FranchiseResolver.resolve();
        final Set<Integer> rootIdsMatchingQuery = rootIdsMatching(q, resolution);

        final List<Integer> rootIds = resolution.rootIds()
                .stream()
                .filter(rootIdsMatchingQuery::contains)
                .filter(rootId -> kind == null || resolution.kindOf(rootId) == kind)
                .toList();

        final Map<Integer, Long> memberCountByRoot = resolution.memberCountByRoot();
        final PageResult<Anime> roots = Anime.findPageByIds(rootIds, page, size);

        final List<FranchiseSummary> data = roots.data()
                .stream()
                .map(root -> new FranchiseSummary()
                        .root(animeOverrideService.toBindingSummary(root))
                        .kind(resolution.kindOf(root.anilistId))
                        .memberCount(memberCountByRoot.get(root.anilistId).intValue()))
                .toList();

        return new FranchiseSummaryPage()
                .page(roots.page())
                .size(roots.size())
                .totalElements(roots.totalElements())
                .totalPages(roots.totalPages())
                .data(data);
    }

    /**
     * Cria uma franquia manual com o anime informado como raiz: a atribuição dele vira
     * {@code MANUAL_ROOT}, então ele sai da franquia em que estava e o galho automático dele vem
     * junto.
     *
     * @param createFranchiseRequest payload com o anime que vira raiz
     * @return a franquia criada
     * @throws ResourceNotFoundException se não há metadata salva pro anime
     * @throws ConflictException se o anime já é raiz
     */
    @Transactional
    public FranchiseDetail createFranchise(CreateFranchiseRequest createFranchiseRequest) {
        final Integer rootId = createFranchiseRequest.getRootAnilistId();

        Log.infof("Criando franquia manual com raiz %d", rootId);

        final Anime root = animeService.findOrThrow(rootId);
        final FranchiseResolution before = FranchiseResolver.resolve();

        if (before.isRoot(rootId)) {
            Log.warnf("Anime %d já é raiz de franquia", rootId);
            throw new ConflictException("O anime informado já é raiz de uma franquia");
        }

        root.franchiseAssignment = FranchiseAssignment.MANUAL_ROOT;
        root.franchiseRoot = null;

        return toDetail(rootId, FranchiseResolver.resolveAgain(before));
    }

    /**
     * Remove uma franquia manual: a raiz ({@code MANUAL_ROOT}) e os membros
     * {@code MANUAL_MEMBER} dela voltam pra {@code AUTO}.
     *
     * @param rootId raiz da franquia
     * @throws ResourceNotFoundException se {@code rootId} não é uma raiz atual
     * @throws BusinessRuleException se a raiz é natural
     */
    @Transactional
    public void deleteFranchise(Integer rootId) {
        Log.infof("Removendo a franquia manual de raiz %d", rootId);

        final Anime root = animeService.findOrThrow(rootId);
        final FranchiseResolution resolution = FranchiseResolver.resolve();

        requireRoot(rootId, resolution);

        if (resolution.kindOf(rootId) != FranchiseKind.MANUAL) {
            throw new BusinessRuleException("Só franquias manuais podem ser removidas; esta raiz é natural");
        }

        Anime.resetManualMembers(root);
        root.franchiseAssignment = FranchiseAssignment.AUTO;
    }

    /**
     * Detalhe da franquia pra organização no portal.
     *
     * @param rootId raiz da franquia
     * @return a franquia detalhada
     * @throws ResourceNotFoundException se {@code rootId} não é uma raiz atual
     */
    public FranchiseDetail getFranchise(Integer rootId) {
        animeService.findOrThrow(rootId);

        final FranchiseResolution resolution = FranchiseResolver.resolve();

        requireRoot(rootId, resolution);

        return toDetail(rootId, resolution);
    }

    /**
     * Muda a atribuição de franquia do anime: volta à regra automática ({@code AUTO}), vai pra
     * franquia de uma raiz ({@code MANUAL_MEMBER}) ou fica fora de qualquer franquia
     * ({@code HIDDEN}). Mover uma raiz manual pra outra franquia junta as duas: ela deixa de ser
     * raiz manual e os membros {@code MANUAL_MEMBER} dela passam pra nova raiz. Uma raiz natural
     * junta pela própria resolução, já que o galho dela segue o pai. Uma raiz manual que volta
     * pra {@code AUTO} ou é oculta deixa de ser raiz, e os membros {@code MANUAL_MEMBER} dela a
     * acompanham. {@code MANUAL_ROOT} só vem da criação de franquia.
     *
     * @param anilistId anime
     * @param animeFranchiseAssignmentRequest payload com a nova atribuição
     * @return a franquia de destino com {@code MANUAL_MEMBER}, a de origem com {@code AUTO}/{@code
     *         HIDDEN}, ou a atual do anime se a de origem deixou de existir; {@code null} se o
     *         anime não está em nenhuma
     * @throws ResourceNotFoundException se não há metadata salva pro anime ou pra raiz de destino
     * @throws BusinessRuleException se a atribuição for {@code MANUAL_ROOT}, se a raiz de destino
     *         faltar com {@code MANUAL_MEMBER}, vier com outra atribuição, não for raiz atual, for
     *         a franquia atual ou o próprio anime
     */
    @Transactional
    public FranchiseDetail setAnimeFranchise(Integer anilistId,
                                             AnimeFranchiseAssignmentRequest animeFranchiseAssignmentRequest) {

        final FranchiseAssignment assignment = animeFranchiseAssignmentRequest.getAssignment();
        final Integer rootId = animeFranchiseAssignmentRequest.getRootId();

        Log.infof("Atribuindo o anime %d como %s na franquia de %s", anilistId, assignment, rootId);

        if (assignment == FranchiseAssignment.MANUAL_ROOT) {
            throw new BusinessRuleException(
                    "Pra tornar o anime raiz de uma franquia manual, crie a franquia");
        }

        final Anime anime = animeService.findOrThrow(anilistId);
        final FranchiseResolution before = FranchiseResolver.resolve();
        final Integer originRootId = before.rootOf(anilistId);

        if (assignment == FranchiseAssignment.MANUAL_MEMBER) {
            final Anime target = requireTargetRoot(anilistId, rootId, originRootId, before);

            if (anime.franchiseAssignment == FranchiseAssignment.MANUAL_ROOT) {
                Anime.moveManualMembers(anime, target);
            }

            anime.franchiseRoot = target;
        } else {
            if (rootId != null) {
                throw new BusinessRuleException(
                        "A raiz de destino só pode ser informada com a atribuição MANUAL_MEMBER");
            }

            anime.franchiseRoot = null;
        }

        anime.franchiseAssignment = assignment;

        final FranchiseResolution after = FranchiseResolver.resolveAgain(before);
        final Integer preferredRootId = assignment == FranchiseAssignment.MANUAL_MEMBER
                ? rootId
                : originRootId;
        final Integer responseRootId = responseRootId(anilistId, preferredRootId, after);

        if (responseRootId == null) {
            return null;
        }

        return toDetail(responseRootId, after);
    }

    /**
     * Lista os animes fora de qualquer franquia — ocultos à mão e por herança —, paginados por
     * {@code anilistId}.
     *
     * @param page página 0-based
     * @param size tamanho da página
     * @return a página de animes
     */
    public AnimeBindingSummaryPage listHiddenAnimes(Integer page, Integer size) {
        final PageResult<Anime> hidden = Anime.findPageByIds(
                FranchiseResolver.resolve().sortedOutsideIds(),
                page,
                size
        );

        return new AnimeBindingSummaryPage()
                .page(hidden.page())
                .size(hidden.size())
                .totalElements(hidden.totalElements())
                .totalPages(hidden.totalPages())
                .data(hidden.data()
                        .stream()
                        .map(animeOverrideService::toBindingSummary)
                        .toList());
    }

    /**
     * Raízes cuja franquia tem a raiz ou algum membro com o texto no título.
     *
     * @param q texto buscado; {@code null} ou vazio casa todas as raízes
     * @param resolution franquia de cada anime
     * @return as raízes que casam
     */
    private static Set<Integer> rootIdsMatching(String q, FranchiseResolution resolution) {
        if (q == null || q.isBlank()) {
            return new HashSet<>(resolution.rootIds());
        }

        return Anime.findIdsByTitle(q.trim())
                .stream()
                .map(resolution::rootOf)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Valida a raiz de destino de uma atribuição {@code MANUAL_MEMBER}.
     *
     * @param anilistId anime que vai ser movido
     * @param rootId raiz de destino pedida
     * @param originRootId franquia atual do anime, ou {@code null} se está fora de todas
     * @param resolution franquia de cada anime, antes da mudança
     * @return a raiz de destino
     * @throws BusinessRuleException se a raiz faltar, for o próprio anime, não for raiz atual ou
     *         já for a franquia do anime
     * @throws ResourceNotFoundException se não há metadata salva pra raiz
     */
    private Anime requireTargetRoot(Integer anilistId,
                                    Integer rootId,
                                    Integer originRootId,
                                    FranchiseResolution resolution) {

        if (rootId == null) {
            throw new BusinessRuleException("A raiz de destino é obrigatória com a atribuição MANUAL_MEMBER");
        }

        if (rootId.equals(anilistId)) {
            throw new BusinessRuleException("Um anime não pode ser movido pra franquia de si mesmo");
        }

        final Anime target = animeService.findOrThrow(rootId);

        if (!resolution.isRoot(rootId)) {
            Log.warnf("Anime %d não é raiz de franquia", rootId);
            throw new BusinessRuleException("A raiz de destino não é raiz de nenhuma franquia");
        }

        if (rootId.equals(originRootId)) {
            throw new BusinessRuleException("O anime já está nessa franquia");
        }

        return target;
    }

    /**
     * Franquia devolvida depois de mudar a atribuição: a preferida, se ainda existe; senão, a
     * atual do anime.
     *
     * @param anilistId anime cuja atribuição mudou
     * @param preferredRootId destino com {@code MANUAL_MEMBER}, origem com {@code AUTO}/{@code HIDDEN}
     * @param resolution franquia de cada anime, já com a mudança
     * @return a raiz da franquia a devolver, ou {@code null} se o anime não está em nenhuma
     */
    private static Integer responseRootId(Integer anilistId,
                                          Integer preferredRootId,
                                          FranchiseResolution resolution) {

        if (preferredRootId != null && resolution.isRoot(preferredRootId)) {
            return preferredRootId;
        }

        return resolution.rootOf(anilistId);
    }

    private static void requireRoot(Integer rootId, FranchiseResolution resolution) {
        if (!resolution.isRoot(rootId)) {
            Log.warnf("Anime %d não é raiz de franquia", rootId);
            throw new ResourceNotFoundException("Não há franquia com a raiz informada");
        }
    }

    /**
     * Monta o detalhe da franquia: membros, animes relacionados em outra franquia ou ocultos (um
     * por anime, com a aresta de menor id), as arestas que tocam os membros e a prévia da
     * classificação.
     *
     * @param rootId raiz atual
     * @param resolution franquia de cada anime
     * @return a franquia detalhada
     */
    private FranchiseDetail toDetail(Integer rootId, FranchiseResolution resolution) {
        final List<Integer> memberIds = resolution.membersOf(rootId);
        final Set<Integer> memberIdSet = Set.copyOf(memberIds);

        final List<AnimeRelation> edges = AnimeRelation.findByAnilistIdsEitherSide(memberIds)
                .stream()
                .sorted(Comparator.comparing(edge -> edge.id))
                .toList();

        final Map<Integer, AnimeRelation> edgeByRelatedId = new TreeMap<>();

        for (AnimeRelation edge : edges) {
            if (!memberIdSet.contains(edge.fromAnilistId)) {
                edgeByRelatedId.putIfAbsent(edge.fromAnilistId, edge);
            } else if (!memberIdSet.contains(edge.toAnilistId)) {
                edgeByRelatedId.putIfAbsent(edge.toAnilistId, edge);
            }
        }

        final Set<Integer> loadedIds = new HashSet<>(memberIds);
        loadedIds.addAll(edgeByRelatedId.keySet());
        edgeByRelatedId.keySet()
                .stream()
                .map(resolution::rootOf)
                .filter(Objects::nonNull)
                .forEach(loadedIds::add);

        final Map<Integer, Anime> animeById = Anime.<Anime>findByIds(List.copyOf(loadedIds))
                .stream()
                .collect(Collectors.toMap(anime -> anime.anilistId, Function.identity()));

        final Map<Integer, Anime> memberById = memberIds.stream()
                .collect(Collectors.toMap(Function.identity(), animeById::get));

        final List<FranchiseMember> members = memberIds.stream()
                .map(id -> new FranchiseMember()
                        .anime(animeOverrideService.toResponse(animeById.get(id), resolution))
                        .assignment(animeById.get(id).franchiseAssignment)
                        .autoParentId(resolution.autoParentOf(id)))
                .toList();

        final List<FranchiseRelatedAnime> related = edgeByRelatedId.entrySet()
                .stream()
                .map(entry -> toRelated(entry.getKey(), entry.getValue(), animeById, resolution))
                .toList();

        final FranchiseSections sections = FranchiseClassifier.classify(rootId, memberById, edges);

        return new FranchiseDetail()
                .root(animeOverrideService.toResponse(animeById.get(rootId), resolution))
                .kind(resolution.kindOf(rootId))
                .members(members)
                .related(related)
                .edges(animeRelationMapper.toEdgeList(edges))
                .seasons(sections.seasonIds())
                .movies(sections.movieIds())
                .specials(sections.specialIds());
    }

    private FranchiseRelatedAnime toRelated(Integer relatedId,
                                            AnimeRelation edge,
                                            Map<Integer, Anime> animeById,
                                            FranchiseResolution resolution) {

        final Integer currentRootId = resolution.rootOf(relatedId);
        final Integer memberId = relatedId.equals(edge.fromAnilistId)
                ? edge.toAnilistId
                : edge.fromAnilistId;

        final FranchiseRelatedAnime related = new FranchiseRelatedAnime()
                .anime(animeOverrideService.toBindingSummary(animeById.get(relatedId)))
                .relationType(edge.relationType.name())
                .memberId(memberId);

        if (currentRootId != null) {
            related.currentRoot(animeOverrideService.toBindingSummary(animeById.get(currentRootId)));
        }

        return related;
    }

}
