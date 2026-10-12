package io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider;

import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.RcloneClient;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.mapper.StorageItemMapper;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.model.StorageProvider;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.impl.LocalStorageProviderClient;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.impl.MegaStorageProviderClient;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.StorageProviderClient;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Resolve, a partir do {@code type} de um {@link StorageProvider}, a implementação concreta de
 * {@link StorageProviderClient} capaz de operar sobre ele (MEGA via rclone, ou disco local).
 */
@ApplicationScoped
public class StorageProviderClientFactory {

    @Inject
    @RestClient
    RcloneClient rcloneClient;

    @Inject
    StorageItemMapper storageItemMapper;

    /**
     * Resolve o client pro provider informado.
     *
     * @param storageProvider provider cujo client será resolvido
     * @return {@link MegaStorageProviderClient} para {@code MEGA}, ou
     *         {@link LocalStorageProviderClient} para {@code LOCAL} — uma instância nova a
     *         cada chamada, não cacheada
     */
    public StorageProviderClient getClient(StorageProvider storageProvider) {
        Log.infof("Resolvendo client pro storage provider %d (%s)", storageProvider.id, storageProvider.type);

        return switch (storageProvider.type) {
            case MEGA -> new MegaStorageProviderClient(storageProvider, storageItemMapper, rcloneClient);
            case LOCAL -> new LocalStorageProviderClient(storageProvider.config);
        };
    }

}