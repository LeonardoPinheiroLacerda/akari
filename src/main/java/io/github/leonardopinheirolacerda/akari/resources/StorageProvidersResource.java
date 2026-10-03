package io.github.leonardopinheirolacerda.akari.resources;

import io.github.leonardopinheirolacerda.akari.api.StorageProvidersApi;
import io.github.leonardopinheirolacerda.akari.api.dto.CreateStorageProviderRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.StorageProviderHealth;
import io.github.leonardopinheirolacerda.akari.api.dto.StorageProviderPage;
import io.github.leonardopinheirolacerda.akari.api.dto.StorageProviderResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.UpdateStorageProviderConfigRequest;
import io.github.leonardopinheirolacerda.akari.services.StorageProvidersService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;
import org.jboss.resteasy.reactive.server.SimpleResourceInfo;

@ApplicationScoped
public class StorageProvidersResource implements StorageProvidersApi {

    @Inject
    StorageProvidersService service;

    // Provider inacessível NÃO é erro: responde 200 com healthy=false.
    // 502 só quando o próprio rclone (rcd) está fora do ar.
    @Override
    public StorageProviderHealth checkStorageProviderHealth(Integer providerId) {
        return service.checkStorageProviderHealth(providerId);
    }

    @Override
    public StorageProviderResponse createStorageProvider(CreateStorageProviderRequest createStorageProviderRequest) {
        return service.createStorageProvider(createStorageProviderRequest);
    }

    @Override
    public void deleteStorageProvider(Integer providerId) {
        service.deleteStorageProvider(providerId);
    }

    @Override
    public StorageProviderResponse getStorageProviderById(Integer providerId) {
        return service.getStorageProviderById(providerId);
    }

    @Override
    public StorageProviderPage listStorageProviders(Integer page, Integer size) {
        return service.listStorageProviders(page, size);
    }

    @Override
    public StorageProviderResponse updateStorageProviderConfig(Integer providerId,
                                                               UpdateStorageProviderConfigRequest updateStorageProviderConfigRequest) {
        return service.updateStorageProviderConfig(providerId, updateStorageProviderConfigRequest);
    }

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
