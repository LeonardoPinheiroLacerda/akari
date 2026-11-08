package io.github.leonardopinheirolacerda.akari.domain.ingestion.service.step;

import io.github.leonardopinheirolacerda.akari.api.dto.TmdbMediaType;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.TmdbFilePairingFill;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.UnpairedVideoFile;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeService;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeTmdbFilePairingFillService;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.IngestionTask;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums.IngestionStep;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums.IngestionStepStatus;
import io.github.leonardopinheirolacerda.akari.domain.mediafolder.model.MediaFolder;
import io.github.leonardopinheirolacerda.akari.utils.RetryUtils;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.Optional;

/**
 * Step {@code TMDB_FILEPAIR} da ingestão: pareia os arquivos de vídeo da pasta com os episódios
 * da série do TMDB vinculada ao anime, pela estratégia do anibridge.
 *
 * <p>Não conhece os demais steps: lê o anime vinculado à pasta (que o {@code TMDB_BIND} deixou
 * vinculado ao TMDB) e registra a própria execução e seus eventos pelo {@code StepRun} que o
 * {@link IngestionStepService#start} devolve. Não tem {@code @Transactional} próprio — cada
 * chamada a outro service abre a sua transação.
 */
@ApplicationScoped
public class TmdbFilePairStepService extends IngestionStepService {

    @Inject
    AnimeService animeService;

    @Inject
    AnimeTmdbFilePairingFillService animeTmdbFilePairingFillService;

    /**
     * Identifica este step como {@code TMDB_FILEPAIR}.
     *
     * @return {@link IngestionStep#TMDB_FILEPAIR}
     */
    @Override
    protected IngestionStep step() {
        return IngestionStep.TMDB_FILEPAIR;
    }

    /**
     * Pareia os arquivos da pasta com episódios do TMDB, numa execução nova do step.
     *
     * <p>Fluxo: pasta sem anime vinculado ou anime sem binding TMDB → {@code FAILED}; binding
     * de filme → {@code SKIPPED}, já que filme não tem episódios; senão pareia os arquivos
     * ({@code AnimeTmdbFilePairingFillService#fillPairings}, com retry de rate limit), grava um
     * evento {@code WARNING} por arquivo que ficou sem par, e termina {@code COMPLETED} — ou
     * {@code UNRESOLVED} se nenhum arquivo da pasta ficou pareado, nem por um pairing que já
     * existia.
     *
     * @param task task em execução, com a pasta já carregada
     * @return status final do step; qualquer falha (anibridge ou TMDB fora) vira {@code FAILED}
     *         com evento {@code ERROR}, sem lançar
     */
    @Override
    public IngestionStepStatus execute(IngestionTask task) {
        final StepRun run = start(task.id);

        IngestionStepStatus status;

        try {
            status = pairFiles(run, task.folder);
        } catch (RuntimeException e) {
            Log.warnf(e, "Pairing TMDB da pasta %d (%s) falhou", task.folder.id, task.folder.name);

            run.error(e.getMessage());

            status = IngestionStepStatus.FAILED;
        }

        run.finish(status);

        return status;
    }

    private IngestionStepStatus pairFiles(StepRun run, MediaFolder folder) {
        run.started("Pairing dos episódios com o TMDB iniciado");

        final Optional<Anime> boundAnime = animeService.findByFolder(folder.id);

        if (boundAnime.isEmpty()) {
            run.error("Pasta sem anime vinculado na AniList");

            return IngestionStepStatus.FAILED;
        }

        final Anime anime = boundAnime.get();

        if (anime.tmdbId == null) {
            run.error("Anime sem vínculo com o TMDB", Map.of("anilistId", anime.anilistId));

            return IngestionStepStatus.FAILED;
        }

        if (anime.tmdbMediaType == TmdbMediaType.MOVIE) {
            run.completed("Anime vinculado a um filme do TMDB, sem episódios pra parear");

            return IngestionStepStatus.SKIPPED;
        }

        run.requestSent(
                "Resolução dos episódios solicitada ao anibridge e ao TMDB",
                Map.of("tmdbId", anime.tmdbId)
        );

        final TmdbFilePairingFill fill = RetryUtils.withRateLimitRetry(
                () -> animeTmdbFilePairingFillService.fillPairings(
                        anime.anilistId,
                        folder.id,
                        folder.name
                )
        );

        run.resultsFound(
                "%d arquivos pareados, %d já pareados, %d sem par".formatted(
                        fill.paired(),
                        fill.kept(),
                        fill.unpaired().size()
                ),
                Map.of(
                        "paired", fill.paired(),
                        "kept", fill.kept(),
                        "unpaired", fill.unpaired().size()
                )
        );

        for (UnpairedVideoFile file : fill.unpaired()) {
            run.warning("Arquivo sem pairing: " + file.reason(), Map.of("file", file));
        }

        if (fill.paired() + fill.kept() == 0) {
            run.matchNotFound("Nenhum arquivo da pasta foi pareado");

            return IngestionStepStatus.UNRESOLVED;
        }

        run.completed(
                "Arquivos pareados com os episódios do TMDB",
                Map.of("paired", fill.paired(), "kept", fill.kept())
        );

        return IngestionStepStatus.COMPLETED;
    }
}
