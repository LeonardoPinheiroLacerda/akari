package io.github.leonardopinheirolacerda.akari.resources;

import io.github.leonardopinheirolacerda.akari.api.StorageProvidersApi;
import io.github.leonardopinheirolacerda.akari.api.dto.CreateStorageProviderRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.StorageProviderHealth;
import io.github.leonardopinheirolacerda.akari.api.dto.StorageProviderPage;
import io.github.leonardopinheirolacerda.akari.api.dto.StorageProviderResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.UpdateStorageProviderConfigRequest;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class StorageProvidersResource implements StorageProvidersApi {

    // Provider inacessível NÃO é erro: responde 200 com healthy=false.
    // 502 só quando o próprio rclone (rcd) está fora do ar.
    @Override
    public StorageProviderHealth checkStorageProviderHealth(Integer providerId) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    // Header: o 201 leva `Location: /v1/storage/providers/{providerId}`. O retorno é o DTO, então o
    // header precisa ser definido à parte (filtro de resposta ou geração com Response).
    @Override
    public StorageProviderResponse createStorageProvider(
            CreateStorageProviderRequest createStorageProviderRequest) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    @Override
    public void deleteStorageProvider(Integer providerId) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    @Override
    public StorageProviderResponse getStorageProviderById(Integer providerId) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    @Override
    public StorageProviderPage listStorageProviders(Integer page, Integer size) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    @Override
    public StorageProviderResponse updateStorageProviderConfig(
            Integer providerId,
            UpdateStorageProviderConfigRequest updateStorageProviderConfigRequest) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }
}
