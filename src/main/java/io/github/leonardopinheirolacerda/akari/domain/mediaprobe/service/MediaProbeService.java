package io.github.leonardopinheirolacerda.akari.domain.mediaprobe.service;

import io.github.leonardopinheirolacerda.akari.api.dto.MediaInfoResponse;
import io.github.leonardopinheirolacerda.akari.domain.mediaprobe.client.FfprobeClient;
import io.github.leonardopinheirolacerda.akari.domain.mediaprobe.client.FfprobeException;
import io.github.leonardopinheirolacerda.akari.domain.mediaprobe.client.dtos.FfprobeOutput;
import io.github.leonardopinheirolacerda.akari.domain.mediaprobe.client.dtos.FfprobePacketsOutput;
import io.github.leonardopinheirolacerda.akari.domain.mediaprobe.mapper.MediaProbeMapper;
import io.github.leonardopinheirolacerda.akari.domain.mediaprobe.parser.FfprobeValueParser;
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
import java.util.Objects;
import java.util.Optional;

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

    /**
     * Quanto ler a partir de onde a busca caiu, em segundos — folga pra alcançar um keyframe
     * mesmo em container cuja busca não cai exatamente num (ex.: MPEG-TS).
     */
    private static final double SEEK_KEYFRAME_READ_SECONDS = 10.0;

    private static final String KEYFRAME_FLAG = "K";

    /** O ffprobe emite {@code pts_time} com 6 casas — arredonda o resultado na mesma precisão. */
    private static final double MICROS_PER_SECOND = 1_000_000.0;

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

    /**
     * Acha o keyframe de vídeo em que a leitura do arquivo começa ao buscar o instante
     * informado — onde o ffmpeg de fato começa quando faz seek ({@code -ss} de entrada) copiando o
     * vídeo sem recodificar.
     *
     * <p>Não é necessariamente o último keyframe antes do instante: a busca do demuxer segue o
     * índice do container (no Matroska, os {@code Cues}), que pode não listar todos os keyframes
     * — keyframes de corte de cena costumam ficar de fora. Por isso o ffprobe faz a mesma busca
     * do ffmpeg ({@code -read_intervals}, para trás) e o resultado é o primeiro keyframe lido a
     * partir de onde ela caiu.
     *
     * @param fileId id do arquivo de vídeo no catalog
     * @param positionSeconds instante buscado, em segundos a partir do início do arquivo
     * @return o instante do keyframe, em segundos a partir do início do arquivo (já descontado o
     *         {@code start_time} do container); vazio se nenhum keyframe for lido logo depois da
     *         busca (arquivo sem vídeo, GOP maior que a janela lida)
     * @throws ResourceNotFoundException se o {@code fileId} não existir no catalog, se o
     *         arquivo de um provider {@code LOCAL} não existir mais em disco, ou se o arquivo
     *         de um provider remoto ainda não tiver terminado de baixar no cache
     * @throws IntegrationNotConfiguredException se a preferência {@code cache.dir} não estiver
     *         configurada
     * @throws IntegrationException se o ffprobe falhar sobre um arquivo já resolvido e existente
     */
    public Optional<Double> findSeekKeyframe(Integer fileId, Double positionSeconds) {
        final VideoFile videoFile = videoFileService.findOrThrow(fileId);
        final Path path = VideoFileLocationUtils.resolvePath(
                videoFile,
                storageProviderClientFactory,
                settingService.getCacheDir()
        );

        final FfprobePacketsOutput output;

        try {
            output = ffprobeClient.probeVideoPackets(path, positionSeconds, SEEK_KEYFRAME_READ_SECONDS);

        } catch (FfprobeException e) {
            Log.errorf(e, "Falha ao listar os pacotes de vídeo de %s", path);
            throw new IntegrationException(e.getMessage(), e);
        }

        if (output.packets() == null) {
            return Optional.empty();
        }

        final double startTime = containerStartTime(output);

        return output.packets()
                .stream()
                .filter(packet -> packet.flags() != null && packet.flags().contains(KEYFRAME_FLAG))
                .map(packet -> FfprobeValueParser.toDouble(packet.ptsTime()))
                .filter(Objects::nonNull)
                .findFirst()
                .map(ptsTime -> Math.round((ptsTime - startTime) * MICROS_PER_SECOND) / MICROS_PER_SECOND);
    }

    /** {@code start_time} do container; {@code 0} se o ffprobe não informar. */
    private double containerStartTime(FfprobePacketsOutput output) {
        if (output.format() == null) {
            return 0.0;
        }

        final Double startTime = FfprobeValueParser.toDouble(output.format().startTime());

        return startTime != null ? startTime : 0.0;
    }

}
