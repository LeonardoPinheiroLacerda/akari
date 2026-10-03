package io.github.leonardopinheirolacerda.akari.providers;

import io.github.leonardopinheirolacerda.akari.clients.rclone.RcloneClient;
import io.github.leonardopinheirolacerda.akari.mapper.StorageItemMapper;
import io.github.leonardopinheirolacerda.akari.model.StorageProvider;

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