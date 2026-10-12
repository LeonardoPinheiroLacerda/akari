package io.github.leonardopinheirolacerda.akari.utils;

import io.github.leonardopinheirolacerda.akari.api.dto.CacheStatus;
import io.github.leonardopinheirolacerda.akari.api.dto.StorageProviderType;
import io.github.leonardopinheirolacerda.akari.domain.cache.model.VideoFileCache;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.StorageProviderClientFactory;
import io.github.leonardopinheirolacerda.akari.domain.videofile.model.VideoFile;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationNotConfiguredException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;

import java.nio.file.Path;

/**
 * Resolve onde um {@link VideoFile} está disponível em disco: direto, se o provider for
 * {@code LOCAL}; pelo cache local, se o provider for remoto e o download já tiver terminado.
 *
 * <p>Compartilhado por {@code mediaprobe} e, futuramente, {@code playback} — os dois precisam
 * exatamente da mesma decisão e dos mesmos erros antes de operar sobre o arquivo (ffprobe num
 * caso, transcode no outro). Estático e sem estado: não injeta nada, recebe os colaboradores
 * ({@link StorageProviderClientFactory}, o diretório de cache já resolvido) como parâmetro.
 * Segue o mesmo precedente de {@code PaginationUtils}: um helper de {@code utils/} pode lançar
 * as exceções da aplicação diretamente quando a regra de erro é a mesma pra todo chamador.
 */
public final class VideoFileLocationUtils {

    private VideoFileLocationUtils() {
    }

    /**
     * Resolve o caminho em disco do arquivo, pronto pra uso (ffprobe, transcode, etc).
     *
     * @param videoFile arquivo de vídeo a localizar
     * @param storageProviderClientFactory fábrica usada para resolver o client do provider,
     *                                     quando for {@code LOCAL}
     * @param cacheDir diretório de cache configurado (preferência {@code cache.dir}), usado
     *                 quando o provider for remoto; pode ser {@code null}/em branco
     * @return o caminho do arquivo em disco
     * @throws ResourceNotFoundException se o arquivo de um provider {@code LOCAL} não existir
     *         mais fisicamente, ou se o arquivo de um provider remoto ainda não tiver terminado
     *         de baixar no cache
     * @throws IntegrationNotConfiguredException se o provider for remoto e {@code cacheDir} for
     *         nulo ou em branco
     */
    public static Path resolvePath(VideoFile videoFile, StorageProviderClientFactory storageProviderClientFactory, String cacheDir) {
        if (videoFile.folder.storageProvider.type == StorageProviderType.LOCAL) {
            return storageProviderClientFactory
                    .getClient(videoFile.folder.storageProvider)
                    .resolveLocalPath(videoFile.path)
                    .orElseThrow(() -> new ResourceNotFoundException("Arquivo não encontrado em disco para o provider local"));
        }

        if (cacheDir == null || cacheDir.isBlank()) {
            throw new IntegrationNotConfiguredException(
                    "Cache local não configurado. Cadastre o diretório na preferência 'cache.dir' pra habilitar o acesso ao arquivo.");
        }

        return VideoFileCache.findByVideoFileId(videoFile.id)
                .filter(cache -> cache.status == CacheStatus.COMPLETED)
                .map(cache -> Path.of(cacheDir, cache.id.toString()))
                .orElseThrow(() -> new ResourceNotFoundException("Arquivo de provider remoto ainda não terminou de baixar no cache"));
    }

}
