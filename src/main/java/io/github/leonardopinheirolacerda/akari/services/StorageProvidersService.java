package io.github.leonardopinheirolacerda.akari.services;

import io.github.leonardopinheirolacerda.akari.api.dto.CreateStorageProviderRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.StorageProviderHealth;
import io.github.leonardopinheirolacerda.akari.api.dto.StorageProviderPage;
import io.github.leonardopinheirolacerda.akari.api.dto.StorageProviderResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.UpdateStorageProviderConfigRequest;
import io.github.leonardopinheirolacerda.akari.clients.rclone.RcloneClient;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.github.leonardopinheirolacerda.akari.mapper.StorageProvidersMapper;
import io.github.leonardopinheirolacerda.akari.model.pageable.PageResult;
import io.github.leonardopinheirolacerda.akari.model.StorageProvider;
import io.github.leonardopinheirolacerda.akari.providers.RemoteStorageProviderClient;
import io.github.leonardopinheirolacerda.akari.providers.StorageProviderClient;
import io.github.leonardopinheirolacerda.akari.providers.StorageProviderClientFactory;
import io.github.leonardopinheirolacerda.akari.providers.StorageProviderConfigs;
import io.github.leonardopinheirolacerda.akari.providers.processors.ObscureFieldApplier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class StorageProvidersService {

    @Inject
    ObscureFieldApplier obscureFieldApplier;

    @Inject
    StorageProviderClientFactory storageProviderClientFactory;

    @Inject
    @RestClient
    RcloneClient rcloneClient;

    @Inject
    StorageProvidersMapper mapper;

    @Transactional
    public StorageProviderResponse createStorageProvider(CreateStorageProviderRequest createStorageProviderRequest) {
        final StorageProvider storageProvider = new StorageProvider();

        storageProvider.config = obscureFieldApplier.obscureRequiredFields(
                createStorageProviderRequest.getConfig(),
                StorageProviderConfigs.configTypeOf(createStorageProviderRequest.getType())
        );

        storageProvider.type = createStorageProviderRequest.getType();

        storageProvider.persist();

        return mapper.toResponse(storageProvider);
    }

    @Transactional
    public StorageProviderResponse updateStorageProviderConfig(
            Integer providerId,
            UpdateStorageProviderConfigRequest updateStorageProviderConfigRequest) {
        final StorageProvider storageProvider = findOrThrow(providerId);

        storageProvider.config = obscureFieldApplier.obscureRequiredFields(
                updateStorageProviderConfigRequest.getConfig(),
                StorageProviderConfigs.configTypeOf(storageProvider.type)
        );

        return mapper.toResponse(storageProvider);
    }

    @Transactional
    public void deleteStorageProvider(Integer providerId) {
        final StorageProvider storageProvider = findOrThrow(providerId);

        storageProvider.delete();
    }

    public StorageProviderResponse getStorageProviderById(Integer providerId) {
        final StorageProvider storageProvider = findOrThrow(providerId);

        return mapper.toResponse(storageProvider);
    }

    public StorageProviderPage listStorageProviders(Integer page, Integer size) {
        final PageResult<StorageProvider> pageResult = StorageProvider.findPage(page, size);

        return mapper.toPage(pageResult);
    }

    public StorageProviderHealth checkStorageProviderHealth(Integer providerId) {
        final StorageProvider storageProvider = findOrThrow(providerId);
        final StorageProviderClient client = storageProviderClientFactory.getClient(storageProvider);

        if (client instanceof RemoteStorageProviderClient) {
            try {
                rcloneClient.healthCheck();
            } catch (ProcessingException | WebApplicationException e) {
                throw new IntegrationException("rclone rcd não está acessível", e);
            }
        }

        return new StorageProviderHealth().healthy(client.checkConnection());
    }

    private StorageProvider findOrThrow(Integer providerId) {
        return StorageProvider.find(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Não foi possível localizar um provedor com o id informado"));
    }

}
