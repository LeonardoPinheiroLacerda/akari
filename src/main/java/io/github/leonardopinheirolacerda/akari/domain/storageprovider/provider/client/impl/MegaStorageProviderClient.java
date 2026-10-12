package io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.impl;

import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.RcloneClient;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.mapper.StorageItemMapper;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.model.StorageProvider;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.annotation.Obscure;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.RemoteStorageProviderClient;

import java.util.Map;

/**
 * {@link RemoteStorageProviderClient} pro backend {@code mega} do rclone.
 */
public class MegaStorageProviderClient extends RemoteStorageProviderClient {

    /**
     * Cria o client pra um provider MEGA.
     *
     * @param storageProvider provider cuja config traz as credenciais ({@code email}/
     *                        {@code password})
     * @param mapper conversor de item cru do rclone pro model de domínio
     * @param rcloneClient client REST do rclone rcd
     */
    public MegaStorageProviderClient(StorageProvider storageProvider, StorageItemMapper mapper, RcloneClient rcloneClient) {
        super(storageProvider, mapper, rcloneClient);
    }

    /**
     * Monta o filesystem descriptor do rclone pro backend {@code mega}.
     *
     * @param config config do provider; espera as chaves {@code email} e {@code password} — a
     *               senha já chega obscurecida (ver {@link Obscure}), formato que o rclone
     *               aceita direto em {@code pass=}
     * @return o {@code fs} no formato {@code ":mega,user=<email>,pass=<password>:"}
     */
    @Override
    protected String getProviderFs(Map<String, String> config) {
        return ":mega,user=%s,pass=%s:".formatted(config.get("email"), config.get("password"));
    }

}