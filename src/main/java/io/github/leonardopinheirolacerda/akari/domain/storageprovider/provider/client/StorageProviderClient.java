package io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client;

import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.model.StorageItem;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Operações que um provider de armazenamento (local ou remoto) precisa suportar: listagem,
 * download pro cache local e resolução de path físico.
 *
 * <p>Implementada por {@code LocalStorageProviderClient} (disco do servidor, sem download — o
 * arquivo já está acessível) e por {@code RemoteStorageProviderClient}/subclasses (ex.: MEGA,
 * via rclone — precisa baixar pro cache antes de o arquivo ficar acessível localmente).
 */
public interface StorageProviderClient {

    /**
     * Lista os filhos diretos de um diretório (arquivos e subdiretórios), sem recursão.
     *
     * @param path path do diretório, relativo à raiz do provider
     * @return os filhos diretos; lista vazia se o diretório não existir ou estiver vazio
     */
    List<StorageItem> listChildren(String path);

    /**
     * Lista só os filhos diretos que são diretórios.
     *
     * @param path path do diretório, relativo à raiz do provider
     * @return os subdiretórios diretos
     */
    List<StorageItem> listChildDirectories(String path);

    /**
     * Lista só os filhos diretos que são arquivos de vídeo (MIME type {@code video/*}).
     *
     * @param path path do diretório, relativo à raiz do provider
     * @return os arquivos de vídeo diretos
     */
    List<StorageItem> listChildVideos(String path);

    /**
     * Lista toda a subárvore a partir do path informado, recursivamente.
     *
     * @param path path do diretório raiz da varredura, relativo à raiz do provider
     * @return todos os itens da subárvore (arquivos e diretórios)
     */
    List<StorageItem> listTree(String path);

    /**
     * Baixa um arquivo remoto pro cache local, de forma assíncrona.
     *
     * @param remoteFilePath path do arquivo no provider, relativo à sua raiz
     * @param localDestinationDir diretório local de destino (o {@code cache.dir} configurado)
     * @param localFileName nome do arquivo local de destino
     * @return id do job assíncrono criado, usado para acompanhar o progresso/conclusão
     * @throws UnsupportedOperationException se o provider for {@code LOCAL} — arquivos locais
     *         não usam cache, não há o que baixar
     */
    Long downloadFile(String remoteFilePath, String localDestinationDir, String localFileName);

    /**
     * Confirma se o provider está acessível no momento.
     *
     * @return {@code true} se uma operação de teste (listagem da raiz, ou checagem do diretório
     *         raiz local) teve sucesso
     */
    boolean checkConnection();

    /**
     * Resolve o caminho físico, no disco local, de um arquivo do provider — sem baixar nada.
     *
     * @param relativePath path do arquivo, relativo à raiz do provider
     * @return o caminho físico, se o provider for {@code LOCAL} e o arquivo existir; vazio para
     *         providers remotos (que nunca resolvem path local sozinhos) ou se o arquivo não
     *         existir fisicamente
     */
    Optional<Path> resolveLocalPath(String relativePath);
}