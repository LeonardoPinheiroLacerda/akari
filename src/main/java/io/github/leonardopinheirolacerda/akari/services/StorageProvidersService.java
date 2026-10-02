package io.github.leonardopinheirolacerda.akari.services;

import io.github.leonardopinheirolacerda.akari.api.dto.CreateStorageProviderRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.StorageProviderPage;
import io.github.leonardopinheirolacerda.akari.api.dto.StorageProviderResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.UpdateStorageProviderConfigRequest;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.github.leonardopinheirolacerda.akari.mapper.StorageProvidersMapper;
import io.github.leonardopinheirolacerda.akari.model.pageable.PageResult;
import io.github.leonardopinheirolacerda.akari.model.StorageProvider;
import io.github.leonardopinheirolacerda.akari.providers.StorageProviderConfigs;
import io.github.leonardopinheirolacerda.akari.providers.processors.ObscureFieldApplier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.Map;

@ApplicationScoped
public class StorageProvidersService {

    @Inject
    ObscureFieldApplier obscureFieldApplier;

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

    private StorageProvider findOrThrow(Integer providerId) {
        return StorageProvider.find(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Não foi possível localizar um provedor com o id informado"));
    }

}
