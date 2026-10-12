package io.github.leonardopinheirolacerda.akari.domain.storageprovider.service;

import io.github.leonardopinheirolacerda.akari.api.dto.CreateStorageProviderRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.StorageProviderHealth;
import io.github.leonardopinheirolacerda.akari.api.dto.StorageProviderPage;
import io.github.leonardopinheirolacerda.akari.api.dto.StorageProviderResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.UpdateStorageProviderConfigRequest;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.RcloneClient;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.mapper.StorageProviderMapper;
import io.github.leonardopinheirolacerda.akari.model.PageResult;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.model.StorageProvider;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.RemoteStorageProviderClient;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.StorageProviderClient;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.StorageProviderClientFactory;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.StorageProviderConfigs;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.processor.ObscureFieldApplier;
import io.github.leonardopinheirolacerda.akari.utils.DirectoryUtils;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Cadastro, config e saúde dos storage providers (MEGA via rclone, ou disco local).
 *
 * <p>A config de cada provider é um {@code Map<String, String>} cujo shape varia por
 * {@code type} (ver {@link StorageProviderConfigs}); campos sensíveis são obscurecidos via
 * {@link ObscureFieldApplier} antes de persistir.
 */
@ApplicationScoped
public class StorageProviderService {

    @Inject
    ObscureFieldApplier obscureFieldApplier;

    @Inject
    StorageProviderClientFactory storageProviderClientFactory;

    @Inject
    @RestClient
    RcloneClient rcloneClient;

    @Inject
    StorageProviderMapper storageProviderMapper;

    /**
     * Cadastra um novo storage provider, obscurecendo e validando a config de acordo com o
     * {@code type} informado.
     *
     * @param createStorageProviderRequest tipo do provider e config em claro
     * @return o provider recém-criado
     * @throws ConstraintViolationException se a config não bater com o shape esperado pro
     *         {@code type} (ex.: campo obrigatório ausente)
     */
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

        return storageProviderMapper.toResponse(storageProvider);
    }

    /**
     * Substitui a config de um storage provider existente, obscurecendo e validando de acordo
     * com o {@code type} já cadastrado do provider (o {@code type} em si não muda).
     *
     * @param providerId id do provider a atualizar
     * @param updateStorageProviderConfigRequest nova config em claro
     * @return o provider com a config atualizada
     * @throws ResourceNotFoundException se o provider não existir
     * @throws ConstraintViolationException se a config não bater com o shape esperado pro
     *         {@code type} já cadastrado
     */
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

        return storageProviderMapper.toResponse(storageProvider);
    }

    /**
     * Remove um storage provider.
     *
     * @param providerId id do provider a remover
     * @throws ResourceNotFoundException se o provider não existir
     */
    @Transactional
    public void deleteStorageProvider(Integer providerId) {
        Log.infof("Removendo storage provider %d", providerId);

        final StorageProvider storageProvider = findOrThrow(providerId);

        storageProvider.delete();

        Log.infof("Storage provider %d removido com sucesso", providerId);
    }

    /**
     * Busca um storage provider pelo id.
     *
     * @param providerId id do provider
     * @return o provider encontrado
     * @throws ResourceNotFoundException se o provider não existir
     */
    public StorageProviderResponse getStorageProviderById(Integer providerId) {
        final StorageProvider storageProvider = findOrThrow(providerId);

        return storageProviderMapper.toResponse(storageProvider);
    }

    /**
     * Pagina todos os storage providers cadastrados.
     *
     * @param page página desejada, 0-based
     * @param size tamanho da página
     * @return página de providers
     */
    public StorageProviderPage listStorageProviders(Integer page, Integer size) {
        final PageResult<StorageProvider> pageResult = StorageProvider.findPage(page, size);

        Log.infof("Listados %d storage providers (página %d, tamanho %d)", pageResult.data().size(), page, size);

        return storageProviderMapper.toPage(pageResult);
    }

    /**
     * Verifica se um storage provider está acessível.
     *
     * <p>Provider inacessível não é erro — devolve {@code healthy=false}. Só lança quando o
     * próprio rclone rcd está fora do ar, o que impede a checagem de acontecer (e só se aplica
     * a provider remoto; o rcd não entra na checagem de um provider {@code LOCAL}).
     *
     * @param providerId id do provider
     * @return {@code healthy=true} se a conexão/listagem funcionar; {@code false} caso contrário
     * @throws ResourceNotFoundException se o provider não existir
     * @throws IntegrationException se o provider for remoto e o rclone rcd não estiver acessível
     */
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

    /**
     * Confirma se um path existe de fato no provider — nem rclone nem o provider {@code LOCAL}
     * distinguem diretório vazio de inexistente na própria listagem do path, então a checagem é
     * via listagem do pai, procurando o path exato entre os filhos.
     *
     * @param client client do provider já resolvido
     * @param path path a confirmar
     * @return {@code true} se o path existe
     */
    public boolean existsPath(StorageProviderClient client, String path) {
        final String parentPath = DirectoryUtils.parentOf(path);

        return client.listChildDirectories(parentPath)
                .stream()
                .anyMatch(item -> item.path().equals(path));
    }

    /**
     * Busca o model de um storage provider pelo id, ou lança se não existir.
     *
     * @param providerId id do provider
     * @return o provider encontrado
     * @throws ResourceNotFoundException se o provider não existir
     */
    public StorageProvider findOrThrow(Integer providerId) {
        return StorageProvider.find(providerId)
                .orElseThrow(() -> {
                    Log.warnf("Storage provider %d não encontrado", providerId);
                    return new ResourceNotFoundException("Não foi possível localizar um provedor com o id informado");
                });
    }

}
