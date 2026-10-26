package io.github.leonardopinheirolacerda.akari.domain.ingestion.service;

import io.github.leonardopinheirolacerda.akari.api.dto.MediaType;
import io.github.leonardopinheirolacerda.akari.api.dto.StorageProviderType;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.FolderReconciliationResult;
import io.github.leonardopinheirolacerda.akari.domain.mediafolder.model.MediaFolder;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.model.StorageProvider;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.model.StorageItem;
import io.github.leonardopinheirolacerda.akari.domain.videofile.model.VideoFile;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa {@link ReconciliationService} — backfill da chave de ordenação natural de um arquivo já
 * catalogado antes dessa coluna existir, e criação de arquivo novo já com a chave calculada.
 */
@QuarkusTest
public class ReconciliationServiceTest {

    @Inject
    ReconciliationService reconciliationService;

    @Test
    @TestTransaction
    public void deveFazerBackfillDaChaveDeOrdenacaoDeArquivoJaCatalogado() {
        final StorageProvider storageProvider = createStorageProvider();

        final MediaFolder folder = new MediaFolder();

        folder.storageProvider = storageProvider;
        folder.mediaType = MediaType.ANIME;
        folder.name = "Bleach";
        folder.path = "Bleach";
        folder.persist();

        final VideoFile legacyFile = new VideoFile();

        legacyFile.folder = folder;
        legacyFile.name = "Bleach.001.mkv";
        legacyFile.path = "Bleach/Bleach.001.mkv";
        legacyFile.persist();

        assertThat(legacyFile.naturalSortKey).isNull();

        final StorageItem dir = new StorageItem(folder.path, folder.name, null, null, null, true, null);
        final StorageItem video = new StorageItem(legacyFile.path, legacyFile.name, 100L, null, null, false, null);

        reconciliationService.reconcileFolder(storageProvider.id, dir, MediaType.ANIME, List.of(video));

        final VideoFile reloaded = VideoFile.find(legacyFile.id).orElseThrow();

        assertThat(reloaded.naturalSortKey).isEqualTo("00000000000000000001");
    }

    @Test
    @TestTransaction
    public void deveCriarArquivoNovoJaComChaveDeOrdenacaoCalculada() {
        final StorageProvider storageProvider = createStorageProvider();

        final StorageItem dir = new StorageItem("Bleach", "Bleach", null, null, null, true, null);
        final StorageItem video = new StorageItem(
                "Bleach/Bleach.001.mkv", "Bleach.001.mkv", 100L, null, null, false, null);

        final FolderReconciliationResult result = reconciliationService.reconcileFolder(
                storageProvider.id, dir, MediaType.ANIME, List.of(video));

        final List<VideoFile> files = VideoFile.findByFolderId(result.folderId());

        assertThat(files).hasSize(1);
        assertThat(files.get(0).naturalSortKey).isEqualTo("00000000000000000001");
    }

    private StorageProvider createStorageProvider() {
        final StorageProvider storageProvider = new StorageProvider();

        storageProvider.type = StorageProviderType.LOCAL;
        storageProvider.config = Map.of("rootPath", "/tmp/akari-test");
        storageProvider.persist();

        return storageProvider;
    }

}
