package io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider;

import io.github.leonardopinheirolacerda.akari.api.dto.StorageProviderType;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.config.LocalConfig;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.config.MegaConfig;

/**
 * Associa cada {@link StorageProviderType} ao record que define o schema de config exigido
 * (campos obrigatórios, validação Bean Validation, campos {@code @Obscure}).
 */
public final class StorageProviderConfigs {

    private StorageProviderConfigs() {
    }

    /**
     * Resolve o record de schema de config correspondente ao tipo de provider.
     *
     * @param type tipo do provider
     * @return {@code MegaConfig.class} ou {@code LocalConfig.class}, conforme o tipo
     */
    public static Class<?> configTypeOf(StorageProviderType type) {
        return switch (type) {
            case MEGA -> MegaConfig.class;
            case LOCAL -> LocalConfig.class;
        };
    }

}