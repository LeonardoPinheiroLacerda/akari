package io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.impl;

import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.RcloneClient;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.mapper.StorageItemMapper;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.model.StorageProvider;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.RemoteStorageProviderClient;

import java.util.Map;

public class MegaStorageProviderClient extends RemoteStorageProviderClient {

    public MegaStorageProviderClient(StorageProvider storageProvider, StorageItemMapper mapper, RcloneClient rcloneClient) {
        super(storageProvider, mapper, rcloneClient);
    }

    @Override
    protected String getProviderFs(Map<String, String> config) {
        return ":mega,user=%s,pass=%s:".formatted(config.get("email"), config.get("password"));
    }

}