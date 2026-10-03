package io.github.leonardopinheirolacerda.akari.providers;

import io.github.leonardopinheirolacerda.akari.clients.rclone.RcloneClient;
import io.github.leonardopinheirolacerda.akari.providers.mappers.StorageItemMapper;
import io.github.leonardopinheirolacerda.akari.model.StorageProvider;
import io.github.leonardopinheirolacerda.akari.providers.clients.impl.LocalStorageProviderClient;
import io.github.leonardopinheirolacerda.akari.providers.clients.impl.MegaStorageProviderClient;
import io.github.leonardopinheirolacerda.akari.providers.clients.StorageProviderClient;
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
        return switch (storageProvider.type) {
            case MEGA -> new MegaStorageProviderClient(storageProvider, storageItemMapper, rcloneClient);
            case LOCAL -> new LocalStorageProviderClient(storageProvider.config);
        };
    }

}