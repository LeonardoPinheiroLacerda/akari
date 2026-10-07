package io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider;

import io.github.leonardopinheirolacerda.akari.api.dto.StorageProviderType;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.config.LocalConfig;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.config.MegaConfig;

public final class StorageProviderConfigs {

    private StorageProviderConfigs() {
    }

    public static Class<?> configTypeOf(StorageProviderType type) {
        return switch (type) {
            case MEGA -> MegaConfig.class;
            case LOCAL -> LocalConfig.class;
        };
    }

}