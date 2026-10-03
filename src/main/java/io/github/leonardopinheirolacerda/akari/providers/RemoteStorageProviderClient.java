package io.github.leonardopinheirolacerda.akari.providers;

import io.github.leonardopinheirolacerda.akari.clients.rclone.RcloneClient;
import io.github.leonardopinheirolacerda.akari.clients.rclone.dtos.RcloneCopyFileRequest;
import io.github.leonardopinheirolacerda.akari.clients.rclone.dtos.RcloneListOptionsRequest;
import io.github.leonardopinheirolacerda.akari.clients.rclone.dtos.RcloneListRequest;
import io.github.leonardopinheirolacerda.akari.clients.rclone.dtos.RcloneListResponse;
import io.github.leonardopinheirolacerda.akari.mapper.StorageItemMapper;
import io.github.leonardopinheirolacerda.akari.model.StorageProvider;
import io.github.leonardopinheirolacerda.akari.providers.models.StorageItem;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class RemoteStorageProviderClient implements StorageProviderClient {

    private final StorageProvider storageProvider;
    private final StorageItemMapper mapper;
    private final RcloneClient rcloneClient;

    protected RemoteStorageProviderClient(StorageProvider storageProvider, StorageItemMapper mapper, RcloneClient rcloneClient) {
        this.storageProvider = storageProvider;
        this.mapper = mapper;
        this.rcloneClient = rcloneClient;
    }

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
            return false;
        }
    }

    @Override
    public Optional<Path> resolveLocalPath(String relativePath) {
        return Optional.empty();
    }

    protected abstract String getProviderFs(Map<String, String> config);

    private List<StorageItem> list(String path, boolean recursive) {
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
