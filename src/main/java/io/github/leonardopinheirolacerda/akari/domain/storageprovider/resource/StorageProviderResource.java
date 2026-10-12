package io.github.leonardopinheirolacerda.akari.domain.storageprovider.resource;

import io.github.leonardopinheirolacerda.akari.api.StorageProviderApi;
import io.github.leonardopinheirolacerda.akari.api.dto.CreateStorageProviderRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.StorageProviderHealth;
import io.github.leonardopinheirolacerda.akari.api.dto.StorageProviderPage;
import io.github.leonardopinheirolacerda.akari.api.dto.StorageProviderResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.UpdateStorageProviderConfigRequest;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.service.StorageProviderService;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;
import org.jboss.resteasy.reactive.server.SimpleResourceInfo;

/**
 * Implementa {@link StorageProviderApi} — cadastro, config e saúde dos storage providers em
 * {@code /v1/storage/providers}.
 *
 * <p>Só traduz HTTP ↔ service, sem lógica própria: toda regra de negócio fica no
 * {@link StorageProviderService}.
 */
@ApplicationScoped
public class StorageProviderResource implements StorageProviderApi {

    @Inject
    StorageProviderService storageProviderService;

    /**
     * {@code GET /v1/storage/providers/{providerId}/health} — verifica se o provider está
     * acessível.
     *
     * <p>Provider inacessível não é erro: responde {@code 200} com {@code healthy=false}. Só
     * vira {@code 502} quando o próprio rclone rcd está fora do ar.
     *
     * @param providerId id do provider
     * @return o status de saúde, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se o provider não existir
     * @throws IntegrationException (traduzida em 502) se o rclone rcd não estiver acessível
     */
    @Override
    public StorageProviderHealth checkStorageProviderHealth(Integer providerId) {
        return storageProviderService.checkStorageProviderHealth(providerId);
    }

    /**
     * {@code POST /v1/storage/providers} — cadastra um novo storage provider.
     *
     * @param createStorageProviderRequest tipo do provider e config em claro
     * @return o provider recém-criado, com {@code 201 Created}
     * @throws ConstraintViolationException (traduzida em 400) se a config não bater com o shape
     *         esperado pro {@code type} informado
     */
    @Override
    public StorageProviderResponse createStorageProvider(CreateStorageProviderRequest createStorageProviderRequest) {
        return storageProviderService.createStorageProvider(createStorageProviderRequest);
    }

    /**
     * {@code DELETE /v1/storage/providers/{providerId}} — remove um storage provider.
     *
     * @param providerId id do provider a remover
     * @throws ResourceNotFoundException (traduzida em 404) se o provider não existir
     */
    @Override
    public void deleteStorageProvider(Integer providerId) {
        storageProviderService.deleteStorageProvider(providerId);
    }

    /**
     * {@code GET /v1/storage/providers/{providerId}} — busca um storage provider pelo id.
     *
     * @param providerId id do provider
     * @return o provider encontrado, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se o provider não existir
     */
    @Override
    public StorageProviderResponse getStorageProviderById(Integer providerId) {
        return storageProviderService.getStorageProviderById(providerId);
    }

    /**
     * {@code GET /v1/storage/providers} — lista todos os storage providers cadastrados.
     *
     * @param page página desejada, 0-based
     * @param size tamanho da página
     * @return página de providers, com {@code 200 OK}
     */
    @Override
    public StorageProviderPage listStorageProviders(Integer page, Integer size) {
        return storageProviderService.listStorageProviders(page, size);
    }

    /**
     * {@code PUT /v1/storage/providers/{providerId}/config} — substitui a config de um storage
     * provider existente.
     *
     * @param providerId id do provider a atualizar
     * @param updateStorageProviderConfigRequest nova config em claro
     * @return o provider com a config atualizada, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se o provider não existir
     * @throws ConstraintViolationException (traduzida em 400) se a config não bater com o shape
     *         esperado pro {@code type} já cadastrado
     */
    @Override
    public StorageProviderResponse updateStorageProviderConfig(Integer providerId,
                                                               UpdateStorageProviderConfigRequest updateStorageProviderConfigRequest) {
        return storageProviderService.updateStorageProviderConfig(providerId, updateStorageProviderConfigRequest);
    }

    /**
     * Adiciona o header {@code Location} na resposta de {@link #createStorageProvider} bem
     * sucedida, apontando pro recurso recém-criado — JAX-RS não monta isso sozinho a partir do
     * DTO gerado, por isso o filtro.
     *
     * @param responseContext resposta em construção
     * @param simpleResourceInfo metadados do método do resource que gerou a resposta
     */
    @ServerResponseFilter
    void addLocationHeader(ContainerResponseContext responseContext, SimpleResourceInfo simpleResourceInfo) {
        if(!responseContext.getStatusInfo().getFamily().equals(Response.Status.Family.SUCCESSFUL))
            return;

        if("createStorageProvider".equals(simpleResourceInfo.getMethodName())) {
            StorageProviderResponse response = (StorageProviderResponse) responseContext.getEntity();
            Log.infof("Adicionando header Location pro storage provider %d criado", response.getId());
            responseContext.getHeaders().add("Location", "/v1/storage/providers/" + response.getId());
        }
    }
}
