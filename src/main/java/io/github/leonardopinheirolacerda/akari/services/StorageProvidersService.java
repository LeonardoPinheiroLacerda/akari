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
import io.github.leonardopinheirolacerda.akari.providers.clients.RemoteStorageProviderClient;
import io.github.leonardopinheirolacerda.akari.providers.clients.StorageProviderClient;
import io.github.leonardopinheirolacerda.akari.providers.StorageProviderClientFactory;
import io.github.leonardopinheirolacerda.akari.providers.StorageProviderConfigs;
import io.github.leonardopinheirolacerda.akari.providers.processors.ObscureFieldApplier;
import io.quarkus.logging.Log;
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
        Log.infof("Criando storage provider do tipo %s", createStorageProviderRequest.getType());

        final StorageProvider storageProvider = new StorageProvider();

        storageProvider.config = obscureFieldApplier.obscureRequiredFields(
                createStorageProviderRequest.getConfig(),
                StorageProviderConfigs.configTypeOf(createStorageProviderRequest.getType())
        );

        storageProvider.type = createStorageProviderRequest.getType();

        storageProvider.persist();

        Log.infof("Storage provider %d criado com sucesso", storageProvider.id);

        return mapper.toResponse(storageProvider);
    }

    @Transactional
    public StorageProviderResponse updateStorageProviderConfig(
            Integer providerId,
            UpdateStorageProviderConfigRequest updateStorageProviderConfigRequest) {
        Log.infof("Atualizando a config do storage provider %d", providerId);

        final StorageProvider storageProvider = findOrThrow(providerId);

        storageProvider.config = obscureFieldApplier.obscureRequiredFields(
                updateStorageProviderConfigRequest.getConfig(),
                StorageProviderConfigs.configTypeOf(storageProvider.type)
        );

        Log.infof("Config do storage provider %d atualizada com sucesso", providerId);

        return mapper.toResponse(storageProvider);
    }

    @Transactional
    public void deleteStorageProvider(Integer providerId) {
        Log.infof("Removendo storage provider %d", providerId);

        final StorageProvider storageProvider = findOrThrow(providerId);

        storageProvider.delete();

        Log.infof("Storage provider %d removido com sucesso", providerId);
    }

    public StorageProviderResponse getStorageProviderById(Integer providerId) {
        final StorageProvider storageProvider = findOrThrow(providerId);

        return mapper.toResponse(storageProvider);
    }

    public StorageProviderPage listStorageProviders(Integer page, Integer size) {
        final PageResult<StorageProvider> pageResult = StorageProvider.findPage(page, size);

        Log.infof("Listados %d storage providers (página %d, tamanho %d)", pageResult.data().size(), page, size);

        return mapper.toPage(pageResult);
    }

    public StorageProviderHealth checkStorageProviderHealth(Integer providerId) {
        Log.infof("Verificando saúde do storage provider %d", providerId);

        final StorageProvider storageProvider = findOrThrow(providerId);
        final StorageProviderClient client = storageProviderClientFactory.getClient(storageProvider);

        if (client instanceof RemoteStorageProviderClient) {
            try {
                rcloneClient.healthCheck();
            } catch (ProcessingException | WebApplicationException e) {
                Log.errorf(e, "rclone rcd não está acessível ao verificar o storage provider %d", providerId);
                throw new IntegrationException("rclone rcd não está acessível", e);
            }
        }

        final boolean healthy = client.checkConnection();

        Log.infof("Storage provider %d: healthy=%s", providerId, healthy);

        return new StorageProviderHealth()
                .healthy(healthy);
    }

    private StorageProvider findOrThrow(Integer providerId) {
        return StorageProvider.find(providerId)
                .orElseThrow(() -> {
                    Log.warnf("Storage provider %d não encontrado", providerId);
                    return new ResourceNotFoundException("Não foi possível localizar um provedor com o id informado");
                });
    }

}
