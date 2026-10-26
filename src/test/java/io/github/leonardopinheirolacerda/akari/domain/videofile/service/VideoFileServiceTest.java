package io.github.leonardopinheirolacerda.akari.domain.videofile.service;

import io.github.leonardopinheirolacerda.akari.api.dto.MediaType;
import io.github.leonardopinheirolacerda.akari.api.dto.StorageProviderType;
import io.github.leonardopinheirolacerda.akari.api.dto.VideoFileRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.VideoFileResponse;
import io.github.leonardopinheirolacerda.akari.domain.mediafolder.model.MediaFolder;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.model.StorageProvider;
import io.github.leonardopinheirolacerda.akari.domain.videofile.model.VideoFile;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o cálculo de {@code naturalSortKey} em {@link VideoFileService} — número de episódio
 * extraído via {@code EpisodeNumberExtractor} (zero-preenchido), fallback pro nome em
 * minúsculas sem extração, e recálculo em {@code updateVideoFile}.
 */
@QuarkusTest
public class VideoFileServiceTest {

    @Inject
    VideoFileService videoFileService;

    @Test
    @TestTransaction
    public void deveCalcularChaveComNumeroDeEpisodioExtraidoZeroPreenchido() {
        final MediaFolder folder = createFolder();

        final VideoFileResponse response = videoFileService.createVideoFile(new VideoFileRequest()
                .folderId(folder.id)
                .name("Bleach.009.1080p.BD.VIZ.OPUS2.0.x265.DUAL-Mattmurdock.mkv"));

        final VideoFile videoFile = VideoFile.find(response.getId()).orElseThrow();

        assertThat(videoFile.naturalSortKey).isEqualTo("00000000000000000009");
    }

    @Test
    @TestTransaction
    public void deveCairProNomeEmMinusculasQuandoNaoExtraiNumero() {
        final MediaFolder folder = createFolder();

        final VideoFileResponse response = videoFileService.createVideoFile(new VideoFileRequest()
                .folderId(folder.id)
                .name("Gintama.Kabuki Chou Shitennou Hen.mkv"));

        final VideoFile videoFile = VideoFile.find(response.getId()).orElseThrow();

        assertThat(videoFile.naturalSortKey).isEqualTo("gintama.kabuki chou shitennou hen.mkv");
    }

    @Test
    @TestTransaction
    public void deveRecalcularChaveAoAtualizarNome() {
        final MediaFolder folder = createFolder();

        final VideoFileResponse created = videoFileService.createVideoFile(new VideoFileRequest()
                .folderId(folder.id)
                .name("Bleach.001.mkv"));

        videoFileService.updateVideoFile(created.getId(), new VideoFileRequest()
                .folderId(folder.id)
                .name("Bleach.010.mkv"));

        final VideoFile videoFile = VideoFile.find(created.getId()).orElseThrow();

        assertThat(videoFile.naturalSortKey).isEqualTo("00000000000000000010");
    }

    private MediaFolder createFolder() {
        final StorageProvider storageProvider = new StorageProvider();

        storageProvider.type = StorageProviderType.LOCAL;
        storageProvider.config = Map.of("rootPath", "/tmp/akari-test");
        storageProvider.persist();

        final MediaFolder folder = new MediaFolder();

        folder.storageProvider = storageProvider;
        folder.mediaType = MediaType.ANIME;
        folder.name = "Bleach";
        folder.path = "Bleach";
        folder.persist();

        return folder;
    }

}
