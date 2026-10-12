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

/**
 * Base de {@link StorageProviderClient} para providers remotos acessados via rclone (ex.:
 * {@code MegaStorageProviderClient}).
 *
 * <p>Toda operação delega pro {@link RcloneClient}, montando o filesystem descriptor do rclone
 * (a string {@code fs}) a partir do {@code config} do provider através de
 * {@link #getProviderFs}, que cada subclasse implementa pro seu backend específico.
 */
@RequiredArgsConstructor
public abstract class RemoteStorageProviderClient implements StorageProviderClient {

    private final StorageProvider storageProvider;
    private final StorageItemMapper mapper;
    private final RcloneClient rcloneClient;

    /**
     * Lista os itens diretamente filhos do path, sem descer em subdiretórios.
     *
     * @param path path relativo à raiz do provider
     * @return os itens filhos, na ordem devolvida pelo rclone
     */
    @Override
    public List<StorageItem> listChildren(String path) {
        return list(path, false);
    }

    /**
     * Lista só os subdiretórios diretamente filhos do path.
     *
     * @param path path relativo à raiz do provider
     * @return os itens filhos que são diretórios
     */
    @Override
    public List<StorageItem> listChildDirectories(String path) {
        return list(path, false)
                .stream()
                .filter(StorageItem::isDir)
                .toList();
    }

    /**
     * Lista só os arquivos de vídeo diretamente filhos do path, pelo MIME type.
     *
     * @param path path relativo à raiz do provider
     * @return os itens filhos cujo {@code mimeType} começa com {@code "video/"}
     */
    @Override
    public List<StorageItem> listChildVideos(String path) {
        return list(path, false)
                .stream()
                .filter(item -> !item.isDir())
                .filter(item -> item.mimeType() != null && item.mimeType().startsWith("video/"))
                .toList();
    }

    /**
     * Lista toda a árvore do path, descendo recursivamente em subdiretórios.
     *
     * @param path path relativo à raiz do provider
     * @return todos os itens da subárvore, achatados numa lista só
     */
    @Override
    public List<StorageItem> listTree(String path) {
        return list(path, true);
    }

    /**
     * Inicia, de forma assíncrona, a cópia de um arquivo remoto pro disco local via rclone.
     *
     * @param remoteFilePath path do arquivo, relativo à raiz do provider
     * @param localDestinationDir diretório local de destino
     * @param localFileName nome do arquivo no destino local
     * @return id do job assíncrono criado no rclone, usado depois pra consultar status/progresso
     */
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

    /**
     * Testa a conexão listando a raiz do provider.
     *
     * <p>Limitação conhecida: o backend mega do rclone cacheia a sessão autenticada por e-mail
     * em memória (abaixo do fs/cache do rclone — {@code fscache/clear} não afeta isso,
     * confirmado testando direto no rcd). Se uma sessão válida já foi estabelecida pra esse
     * e-mail, este método pode devolver {@code true} mesmo com a senha atual errada, até o
     * processo do rcd reiniciar. Sem opção de config no backend pra desabilitar esse cache —
     * aceito como trade-off, não compensa reiniciar o rcd por request.
     *
     * @return {@code true} se a listagem da raiz não lançar
     */
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

    /**
     * Nunca resolve um path local: provider remoto não tem arquivo físico acessível
     * diretamente, só via download (cache) ou listagem.
     *
     * @param relativePath path relativo à raiz do provider; ignorado
     * @return sempre {@code Optional.empty()}
     */
    @Override
    public Optional<Path> resolveLocalPath(String relativePath) {
        Log.infof("Storage provider remoto %d não resolve path local pra \"%s\"", this.storageProvider.id, relativePath);

        return Optional.empty();
    }

    /**
     * Monta o filesystem descriptor do rclone (string {@code fs}) pro backend específico desta
     * subclasse, a partir da config bruta do provider.
     *
     * @param config config do provider (ex.: credenciais), como persistida em
     *               {@code StorageProvider#config}
     * @return o {@code fs} no formato que o rclone espera (ex.: {@code ":mega,user=...,pass=...:"})
     */
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
