package io.github.leonardopinheirolacerda.akari.domain.smartlist.resource;

import io.github.leonardopinheirolacerda.akari.api.SmartListApi;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeBindingSummaryPage;
import io.github.leonardopinheirolacerda.akari.api.dto.SmartListRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.SmartListResponse;
import io.github.leonardopinheirolacerda.akari.domain.smartlist.service.SmartListService;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/**
 * Implementa {@link SmartListApi} — listas por critério em {@code /v1/smart-lists}.
 *
 * <p>Só delega pro {@link SmartListService}.
 */
@ApplicationScoped
public class SmartListResource implements SmartListApi {

    @Inject
    SmartListService smartListService;

    /**
     * {@code GET /v1/smart-lists} — lista as listas na ordem de exibição.
     *
     * @return as listas, com {@code 200 OK}
     */
    @Override
    public List<SmartListResponse> listSmartLists() {
        return smartListService.listSmartLists();
    }

    /**
     * {@code POST /v1/smart-lists} — cadastra uma lista.
     *
     * @param smartListRequest nome, posição, filtro e ordenação
     * @return a lista criada, com {@code 201 Created}
     */
    @Override
    public SmartListResponse createSmartList(SmartListRequest smartListRequest) {
        return smartListService.createSmartList(smartListRequest);
    }

    /**
     * {@code GET /v1/smart-lists/{listId}} — busca uma lista.
     *
     * @param listId id da lista
     * @return a lista, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se a lista não existir
     */
    @Override
    public SmartListResponse getSmartList(Integer listId) {
        return smartListService.getSmartList(listId);
    }

    /**
     * {@code PUT /v1/smart-lists/{listId}} — atualiza uma lista.
     *
     * @param listId id da lista
     * @param smartListRequest novo nome, posição, filtro e ordenação
     * @return a lista atualizada, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se a lista não existir
     */
    @Override
    public SmartListResponse updateSmartList(Integer listId, SmartListRequest smartListRequest) {
        return smartListService.updateSmartList(listId, smartListRequest);
    }

    /**
     * {@code DELETE /v1/smart-lists/{listId}} — remove uma lista.
     *
     * @param listId id da lista
     * @throws ResourceNotFoundException (traduzida em 404) se a lista não existir
     */
    @Override
    public void deleteSmartList(Integer listId) {
        smartListService.deleteSmartList(listId);
    }

    /**
     * {@code GET /v1/smart-lists/{listId}/items} — itens da lista, paginados.
     *
     * @param listId id da lista
     * @param page página 0-based
     * @param size tamanho da página
     * @return a página de raízes de franquia, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se a lista não existir
     */
    @Override
    public AnimeBindingSummaryPage listSmartListItems(Integer listId, Integer page, Integer size) {
        return smartListService.listSmartListItems(listId, page, size);
    }

}
