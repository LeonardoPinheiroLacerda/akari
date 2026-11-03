package io.github.leonardopinheirolacerda.akari.domain.anime.resource;

import io.github.leonardopinheirolacerda.akari.api.AnimeFranchiseApi;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeBindingSummaryPage;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeFranchiseAssignmentRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.CreateFranchiseRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.FranchiseDetail;
import io.github.leonardopinheirolacerda.akari.api.dto.FranchiseKind;
import io.github.leonardopinheirolacerda.akari.api.dto.FranchiseSummaryPage;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeFranchiseService;
import io.github.leonardopinheirolacerda.akari.exceptions.BusinessRuleException;
import io.github.leonardopinheirolacerda.akari.exceptions.ConflictException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import jakarta.inject.Inject;

/**
 * Implementa {@link AnimeFranchiseApi} — organização administrativa das franquias, em
 * {@code /v1/metadata/franchises} e {@code PUT /v1/metadata/animes/{anilistId}/franchise}.
 *
 * <p>Só traduz HTTP ↔ service, sem lógica própria: toda regra de negócio e mapeamento ficam no
 * {@link AnimeFranchiseService}.
 */
public class AnimeFranchiseResource implements AnimeFranchiseApi {

    @Inject
    AnimeFranchiseService animeFranchiseService;

    /**
     * {@code GET /v1/metadata/franchises} — lista as franquias da coleção, paginadas.
     *
     * @param q texto buscado no título da raiz e dos membros
     * @param kind tipo de raiz
     * @param page página desejada, 0-based
     * @param size tamanho da página
     * @return a página de franquias, com {@code 200 OK}
     */
    @Override
    public FranchiseSummaryPage listFranchises(String q, FranchiseKind kind, Integer page, Integer size) {
        return animeFranchiseService.listFranchises(q, kind, page, size);
    }

    /**
     * {@code POST /v1/metadata/franchises} — cria uma franquia manual com o anime informado
     * como raiz.
     *
     * @param createFranchiseRequest payload com o anime que vira raiz
     * @return a franquia criada, com {@code 201 Created}
     * @throws ResourceNotFoundException (traduzida em 404) se não há metadata salva pro anime
     * @throws ConflictException (traduzida em 409) se o anime já é raiz
     */
    @Override
    public FranchiseDetail createFranchise(CreateFranchiseRequest createFranchiseRequest) {
        return animeFranchiseService.createFranchise(createFranchiseRequest);
    }

    /**
     * {@code GET /v1/metadata/franchises/hidden} — lista os animes fora de qualquer franquia.
     *
     * @param page página desejada, 0-based
     * @param size tamanho da página
     * @return a página de animes, com {@code 200 OK}
     */
    @Override
    public AnimeBindingSummaryPage listHiddenAnimes(Integer page, Integer size) {
        return animeFranchiseService.listHiddenAnimes(page, size);
    }

    /**
     * {@code GET /v1/metadata/franchises/{rootId}} — detalhe da franquia pra organização no
     * portal.
     *
     * @param rootId raiz da franquia
     * @return a franquia detalhada, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se {@code rootId} não é uma raiz atual
     */
    @Override
    public FranchiseDetail getFranchise(Integer rootId) {
        return animeFranchiseService.getFranchise(rootId);
    }

    /**
     * {@code DELETE /v1/metadata/franchises/{rootId}} — remove uma franquia manual.
     *
     * @param rootId raiz da franquia
     * @throws ResourceNotFoundException (traduzida em 404) se {@code rootId} não é uma raiz atual
     * @throws BusinessRuleException (traduzida em 400) se a raiz é natural
     */
    @Override
    public void deleteFranchise(Integer rootId) {
        animeFranchiseService.deleteFranchise(rootId);
    }

    /**
     * {@code PUT /v1/metadata/animes/{anilistId}/franchise} — move o anime de franquia ou o
     * oculta.
     *
     * @param anilistId anime
     * @param animeFranchiseAssignmentRequest payload com a nova atribuição
     * @return a franquia de destino/origem, com {@code 200 OK}; sem corpo se o anime não está em
     *         nenhuma e a de origem deixou de existir
     * @throws ResourceNotFoundException (traduzida em 404) se não há metadata salva pro anime ou
     *         pra raiz de destino
     * @throws BusinessRuleException (traduzida em 400) se a raiz de destino é inválida
     */
    @Override
    public FranchiseDetail setAnimeFranchise(Integer anilistId,
                                             AnimeFranchiseAssignmentRequest animeFranchiseAssignmentRequest) {

        return animeFranchiseService.setAnimeFranchise(anilistId, animeFranchiseAssignmentRequest);
    }
}
