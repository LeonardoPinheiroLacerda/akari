package io.github.leonardopinheirolacerda.akari.domain.anime.service;

import io.github.leonardopinheirolacerda.akari.api.dto.FranchiseRootCuration;
import io.github.leonardopinheirolacerda.akari.api.dto.MediaType;
import io.github.leonardopinheirolacerda.akari.api.dto.StorageProviderType;
import io.github.leonardopinheirolacerda.akari.api.dto.VideoFilePage;
import io.github.leonardopinheirolacerda.akari.api.dto.VideoFileRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.VideoFileResponse;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.TmdbFilePairing;
import io.github.leonardopinheirolacerda.akari.domain.mediafolder.model.MediaFolder;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.model.StorageProvider;
import io.github.leonardopinheirolacerda.akari.domain.videofile.service.VideoFileService;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa {@link AnimeEpisodeService} — ordem natural pelo nome do arquivo, posição real de um
 * arquivo sem pareamento, estabilidade da paginação e anime inexistente/sem arquivos.
 */
@QuarkusTest
public class AnimeEpisodeServiceTest {

    @Inject
    AnimeEpisodeService animeEpisodeService;

    @Inject
    VideoFileService videoFileService;

    @Test
    @TestTransaction
    public void deveManterArquivoSemPareamentoNaPosicaoRealPeloNome() {
        final MediaFolder folder = createFolder();
        final Anime anime = createAnime(269, folder);

        final VideoFileResponse episode1 = createVideoFile(folder, "Bleach.001.1080p.mkv");
        final VideoFileResponse episode2 = createVideoFile(folder, "Bleach.002.1080p.mkv");
        final VideoFileResponse episode3 = createVideoFile(folder, "Bleach.003.1080p.mkv");
        final VideoFileResponse episode4 = createVideoFile(folder, "Bleach.004.1080p.mkv");
        final VideoFileResponse episode5 = createVideoFile(folder, "Bleach.005.1080p.mkv");

        createPairing(episode1, 1, 1);
        createPairing(episode2, 1, 2);
        createPairing(episode4, 1, 4);
        createPairing(episode5, 1, 5);

        final VideoFilePage page = animeEpisodeService.listAnimeEpisodes(anime.anilistId, 0, 10);

        assertThat(page.getData())
                .extracting(VideoFileResponse::getId)
                .containsExactly(
                        episode1.getId(),
                        episode2.getId(),
                        episode3.getId(),
                        episode4.getId(),
                        episode5.getId());

        assertThat(page.getData().get(2).getTmdbPairing()).isNull();
    }

    @Test
    @TestTransaction
    public void deveOrdenarNumerosDeTamanhosDiferentesComoNumero() {
        final MediaFolder folder = createFolder();
        final Anime anime = createAnime(269, folder);

        final VideoFileResponse episode10 = createVideoFile(folder, "Bleach.010.mkv");
        final VideoFileResponse episode1 = createVideoFile(folder, "Bleach.001.mkv");
        final VideoFileResponse episode9 = createVideoFile(folder, "Bleach.009.mkv");

        final VideoFilePage page = animeEpisodeService.listAnimeEpisodes(anime.anilistId, 0, 10);

        assertThat(page.getData())
                .extracting(VideoFileResponse::getId)
                .containsExactly(episode1.getId(), episode9.getId(), episode10.getId());
    }

    @Test
    @TestTransaction
    public void devePaginarDeFormaEstavelUsandoIdComoDesempate() {
        final MediaFolder folder = createFolder();
        final Anime anime = createAnime(269, folder);

        final VideoFileResponse first = createVideoFile(folder, "Bleach.OVA.mkv");
        final VideoFileResponse second = createVideoFile(folder, "Bleach.OVA.mkv");

        final VideoFilePage firstPage = animeEpisodeService.listAnimeEpisodes(anime.anilistId, 0, 1);
        final VideoFilePage secondPage = animeEpisodeService.listAnimeEpisodes(anime.anilistId, 1, 1);

        assertThat(firstPage.getData())
                .extracting(VideoFileResponse::getId)
                .containsExactly(first.getId());

        assertThat(secondPage.getData())
                .extracting(VideoFileResponse::getId)
                .containsExactly(second.getId());
    }

    @Test
    @TestTransaction
    public void deveDevolverPaginaVaziaQuandoPastaDoAnimeNaoTemArquivos() {
        final MediaFolder folder = createFolder();
        final Anime anime = createAnime(269, folder);

        final VideoFilePage page = animeEpisodeService.listAnimeEpisodes(anime.anilistId, 0, 10);

        assertThat(page.getData()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    @TestTransaction
    public void deveLancarNotFoundQuandoAnimeNaoExiste() {
        assertThatThrownBy(() -> animeEpisodeService.listAnimeEpisodes(999999, 0, 10))
                .isInstanceOf(ResourceNotFoundException.class);
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

    private Anime createAnime(Integer anilistId, MediaFolder folder) {
        final Anime anime = new Anime();

        anime.anilistId = anilistId;
        anime.folder = folder;
        anime.franchiseRootCuration = FranchiseRootCuration.AUTO;
        anime.persist();

        return anime;
    }

    private VideoFileResponse createVideoFile(MediaFolder folder, String name) {
        return videoFileService.createVideoFile(new VideoFileRequest()
                .folderId(folder.id)
                .name(name));
    }

    private void createPairing(VideoFileResponse videoFile, Integer seasonNumber, Integer episodeNumber) {
        final TmdbFilePairing pairing = new TmdbFilePairing();

        pairing.fileId = videoFile.getId();
        pairing.seasonNumber = seasonNumber;
        pairing.episodeNumber = episodeNumber;
        pairing.persist();
    }

}
