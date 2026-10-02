package io.github.leonardopinheirolacerda.akari.providers;

import io.github.leonardopinheirolacerda.akari.api.dto.StorageProviderType;
import io.github.leonardopinheirolacerda.akari.providers.configs.LocalConfig;
import io.github.leonardopinheirolacerda.akari.providers.configs.MegaConfig;

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