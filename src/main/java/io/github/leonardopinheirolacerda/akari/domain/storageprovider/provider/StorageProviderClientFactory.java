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

@ApplicationScoped
public class StorageProviderClientFactory {

    @Inject
    @RestClient
    RcloneClient rcloneClient;

    @Inject
    StorageItemMapper storageItemMapper;

    public StorageProviderClient getClient(StorageProvider storageProvider) {
        Log.infof("Resolvendo client pro storage provider %d (%s)", storageProvider.id, storageProvider.type);

        return switch (storageProvider.type) {
            case MEGA -> new MegaStorageProviderClient(storageProvider, storageItemMapper, rcloneClient);
            case LOCAL -> new LocalStorageProviderClient(storageProvider.config);
        };
    }

}