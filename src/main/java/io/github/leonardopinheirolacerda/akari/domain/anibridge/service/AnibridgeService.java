package io.github.leonardopinheirolacerda.akari.domain.anibridge.service;

import io.github.leonardopinheirolacerda.akari.domain.anibridge.client.AnibridgeClient;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.client.dtos.GithubAssetResponse;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.client.dtos.GithubReleaseResponse;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.MappingRelease;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.MappingSegment;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.parser.AnibridgeMappingParser;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Refresh do dataset de mapeamento anime↔TMDB do anibridge. Baixa a release mais recente do
 * GitHub, substitui os {@link MappingSegment} e atualiza o {@link MappingRelease} singleton.
 * Não expõe nenhum endpoint — é consumido só internamente por outro domínio. Disparado pelo
 * {@code AnibridgeScheduler} (boot + cron diário), não tem {@code @Scheduled} aqui.
 */
@ApplicationScoped
public class AnibridgeService {

    private static final String MAPPINGS_ASSET_NAME = "mappings.min.json";

    @Inject
    @RestClient
    AnibridgeClient anibridgeClient;

    @Inject
    AnibridgeMappingParser anibridgeMappingParser;

    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    /**
     * Compara a release mais recente do GitHub com a última ingerida e, se mudou, baixa o
     * {@code mappings.min.json}, reparseia e substitui todos os segments. Protegido por um
     * mutex simples — chamadas concorrentes (scheduled e boot, por exemplo) são ignoradas em
     * vez de rodar em paralelo.
     */
    @Transactional
    public void refresh() {
        if (!refreshing.compareAndSet(false, true)) {
            Log.info("Refresh do anibridge já em andamento, ignorando chamada concorrente");
            return;
        }

        try {
            final GithubReleaseResponse release;
            try {
                release = anibridgeClient.getLatestRelease();
            } catch (ProcessingException | WebApplicationException e) {
                Log.error("Falha ao consultar a release mais recente do anibridge", e);
                throw new IntegrationException("Falha ao consultar a release mais recente do anibridge", e);
            }

            final MappingRelease mappingRelease = MappingRelease.getOrCreate();

            if (release.updatedAt().equals(mappingRelease.upstreamUpdatedAt)) {
                Log.infof("Dataset do anibridge sem mudanças desde %s, refresh ignorado", release.updatedAt());
                return;
            }

            final GithubAssetResponse mappingsAsset = findMappingsAsset(release);
            final List<MappingSegment> segments = downloadAndParse(mappingsAsset.browserDownloadUrl());

            MappingSegment.deleteAll();
            MappingSegment.persist(segments);

            mappingRelease.releaseTag = release.tagName();
            mappingRelease.publishedAt = release.publishedAt();
            mappingRelease.upstreamUpdatedAt = release.updatedAt();
            mappingRelease.ingestedAt = OffsetDateTime.now();
            mappingRelease.segmentCount = segments.size();
            mappingRelease.persist();

            Log.infof("Refresh do anibridge concluído: %d segments da release %s", segments.size(), release.tagName());
        } finally {
            refreshing.set(false);
        }
    }

    private static GithubAssetResponse findMappingsAsset(GithubReleaseResponse release) {
        final Optional<GithubAssetResponse> asset = release.assets().stream()
                .filter(candidate -> MAPPINGS_ASSET_NAME.equals(candidate.name()))
                .findFirst();

        if (asset.isEmpty()) {
            throw new IntegrationException("Release do anibridge sem o asset " + MAPPINGS_ASSET_NAME);
        }

        return asset.get();
    }

    private List<MappingSegment> downloadAndParse(String downloadUrl) {
        final Path tempFile;
        try {
            tempFile = Files.createTempFile("anibridge-mappings", ".json");
        } catch (IOException e) {
            throw new IntegrationException("Falha ao criar arquivo temporário pro download do anibridge", e);
        }

        try {
            final HttpClient httpClient = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            final URI uri = URI.create(downloadUrl);
            final HttpRequest request = HttpRequest
                    .newBuilder(uri)
                    .GET()
                    .build();

            final HttpResponse.BodyHandler<Path> responseBodyHandler = HttpResponse.BodyHandlers.ofFile(tempFile);
            final HttpResponse<Path> response = httpClient.send(request, responseBodyHandler);

            if (response.statusCode() != 200) {
                throw new IntegrationException(
                        "Download do " + MAPPINGS_ASSET_NAME + " falhou com status " + response.statusCode());
            }

            try (InputStream mappingsStream = Files.newInputStream(tempFile)) {
                return anibridgeMappingParser.parse(mappingsStream);
            }

        } catch (IOException | InterruptedException e) {
            throw new IntegrationException("Falha ao baixar o " + MAPPINGS_ASSET_NAME + " do anibridge", e);
        } finally {
            deleteQuietly(tempFile);
        }
    }

    private static void deleteQuietly(Path tempFile) {
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException e) {
            Log.warn("Falha ao limpar o arquivo temporário do download do anibridge", e);
        }
    }

}
