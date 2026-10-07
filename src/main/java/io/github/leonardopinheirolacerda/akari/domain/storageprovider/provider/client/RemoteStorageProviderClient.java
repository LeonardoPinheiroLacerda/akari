package io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client;

import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.RcloneClient;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.dtos.RcloneCopyFileRequest;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.dtos.RcloneListOptionsRequest;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.dtos.RcloneListRequest;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.dtos.RcloneListResponse;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.mapper.StorageItemMapper;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.model.StorageProvider;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.model.StorageItem;
import io.quarkus.logging.Log;
import lombok.RequiredArgsConstructor;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
public abstract class RemoteStorageProviderClient implements StorageProviderClient {

    private final StorageProvider storageProvider;
    private final StorageItemMapper mapper;
    private final RcloneClient rcloneClient;

    @Override
    public List<StorageItem> listChildren(String path) {
        return list(path, false);
    }

    @Override
    public List<StorageItem> listChildDirectories(String path) {
        return list(path, false)
                .stream()
                .filter(StorageItem::isDir)
                .toList();
    }

    @Override
    public List<StorageItem> listChildVideos(String path) {
        return list(path, false)
                .stream()
                .filter(item -> !item.isDir())
                .filter(item -> item.mimeType() != null && item.mimeType().startsWith("video/"))
                .toList();
    }

    @Override
    public List<StorageItem> listTree(String path) {
        return list(path, true);
    }

    @Override
    public Long downloadFile(String remoteFilePath, String localDestinationDir, String localFileName) {
        Log.infof("Baixando %s do storage provider %d para %s/%s", remoteFilePath, this.storageProvider.id, localDestinationDir, localFileName);

        final RcloneCopyFileRequest request = new RcloneCopyFileRequest(
                getProviderFs(this.storageProvider.config),
                remoteFilePath,
                localDestinationDir,
                localFileName,
                true
        );
        return this.rcloneClient.downloadFile(request).jobid();
    }

    // Limitação conhecida: o backend mega do rclone cacheia a sessão autenticada por
    // e-mail em memória (abaixo do fs/cache do rclone — fscache/clear não afeta isso,
    // confirmado testando direto no rcd). Se uma sessão válida já foi estabelecida pra
    // esse e-mail, checkConnection() pode devolver true mesmo com a senha atual errada,
    // até o processo do rcd reiniciar. Sem opção de config no backend pra desabilitar
    // esse cache — aceito como trade-off, não compensa reiniciar o rcd por request.
    @Override
    public boolean checkConnection() {
        try {
            list("", false);
            return true;
        } catch (Exception e) {
            Log.warnf(e, "Falha ao conectar no storage provider %d via rclone", this.storageProvider.id);
            return false;
        }
    }

    @Override
    public Optional<Path> resolveLocalPath(String relativePath) {
        Log.infof("Storage provider remoto %d não resolve path local pra \"%s\"", this.storageProvider.id, relativePath);

        return Optional.empty();
    }

    protected abstract String getProviderFs(Map<String, String> config);

    private List<StorageItem> list(String path, boolean recursive) {
        Log.infof("Listando \"%s\" no storage provider %d (recursivo=%s)", path, this.storageProvider.id, recursive);

        final RcloneListOptionsRequest options = new RcloneListOptionsRequest(recursive);

        final String providerFs = getProviderFs(this.storageProvider.config);

        final RcloneListRequest request = new RcloneListRequest(providerFs, path, options);
        final RcloneListResponse response = this.rcloneClient.listFiles(request);

        return response.list()
                .stream()
                .map(this.mapper::toStorageItem)
                .toList();
    }
}
