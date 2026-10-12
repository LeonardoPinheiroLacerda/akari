package io.github.leonardopinheirolacerda.akari.domain.mediaprobe.service;

import io.github.leonardopinheirolacerda.akari.api.dto.MediaInfoResponse;
import io.github.leonardopinheirolacerda.akari.domain.mediaprobe.client.FfprobeClient;
import io.github.leonardopinheirolacerda.akari.domain.mediaprobe.client.FfprobeException;
import io.github.leonardopinheirolacerda.akari.domain.mediaprobe.client.dtos.FfprobeOutput;
import io.github.leonardopinheirolacerda.akari.domain.mediaprobe.mapper.MediaProbeMapper;
import io.github.leonardopinheirolacerda.akari.domain.setting.service.SettingService;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.StorageProviderClientFactory;
import io.github.leonardopinheirolacerda.akari.domain.videofile.model.VideoFile;
import io.github.leonardopinheirolacerda.akari.domain.videofile.service.VideoFileService;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationNotConfiguredException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.github.leonardopinheirolacerda.akari.utils.VideoFileLocationUtils;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.file.Path;

/**
 * Extração de metadados técnicos de um arquivo de vídeo via {@code ffprobe}.
 *
 * <p>Stateless — nada é persistido, cada chamada resolve o arquivo em disco e roda o probe do
 * zero. A decisão de onde o arquivo está (provider {@code LOCAL} direto, ou cache local pra
 * provider remoto) é do {@link VideoFileLocationUtils}, compartilhada com outros domínios que
 * precisarem da mesma resolução.
 */
@ApplicationScoped
public class MediaProbeService {

    @Inject
    FfprobeClient ffprobeClient;

    @Inject
    MediaProbeMapper mediaProbeMapper;

    @Inject
    StorageProviderClientFactory storageProviderClientFactory;

    @Inject
    SettingService settingService;

    @Inject
    VideoFileService videoFileService;

    /**
     * Resolve o arquivo em disco e roda o probe sobre ele.
     *
     * @param fileId id do arquivo de vídeo no catalog
     * @return container e streams extraídos pelo ffprobe
     * @throws ResourceNotFoundException se o {@code fileId} não existir no catalog, se o
     *         arquivo de um provider {@code LOCAL} não existir mais em disco, ou se o arquivo
     *         de um provider remoto ainda não tiver terminado de baixar no cache
     * @throws IntegrationNotConfiguredException se a preferência {@code cache.dir} não estiver
     *         configurada
     * @throws IntegrationException se o ffprobe falhar sobre um arquivo já resolvido e existente
     */
    public MediaInfoResponse getMediaProbe(Integer fileId) {
        final VideoFile videoFile = videoFileService.findOrThrow(fileId);
        final Path path = VideoFileLocationUtils.resolvePath(
                videoFile,
                storageProviderClientFactory,
                settingService.getCacheDir()
        );

        final FfprobeOutput output;

        try {
            output = ffprobeClient.probe(path);

        } catch (FfprobeException e) {
            Log.errorf(e, "Falha ao extrair o probe de %s", path);
            throw new IntegrationException(e.getMessage(), e);
        }

        final MediaInfoResponse response = mediaProbeMapper.toResponse(output);

        Log.infof(
                "Probe do arquivo %d extraído: %d faixa(s) de vídeo, %d de áudio, %d de legenda",
                fileId,
                response.getVideo().size(),
                response.getAudio().size(),
                response.getSubtitles().size()
        );

        return response;
    }

}
