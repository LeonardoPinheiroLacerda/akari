package io.github.leonardopinheirolacerda.akari.domain.ingestion.service.step;

import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.TmdbOverridesFill;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeService;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeTmdbOverridesFillService;
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
 * Step {@code TMDB_OVERRIDES} da ingestão: preenche título, sinopse, backdrop e logo do anime da
 * pasta a partir das traduções e imagens do TMDB.
 *
 * <p>Não conhece os demais steps: lê o anime vinculado à pasta (que o {@code TMDB_BIND} deixou
 * vinculado ao TMDB) e registra a própria execução e seus eventos pelo {@code StepRun} que o
 * {@link IngestionStepService#start} devolve. Não tem {@code @Transactional} próprio — cada
 * chamada a outro service abre a sua transação.
 */
@ApplicationScoped
public class TmdbOverridesStepService extends IngestionStepService {

    // Nome amigável de cada campo dos overrides, pra mensagem do campo sem opção no TMDB.
    private static final Map<String, String> FIELD_NAMES = Map.of(
            "title", "título",
            "synopsis", "sinopse",
            "backdropPath", "imagem de fundo",
            "logoPath", "logo"
    );

    @Inject
    AnimeService animeService;

    @Inject
    AnimeTmdbOverridesFillService animeTmdbOverridesFillService;

    /**
     * Identifica este step como {@code TMDB_OVERRIDES}.
     *
     * @return {@link IngestionStep#TMDB_OVERRIDES}
     */
    @Override
    protected IngestionStep step() {
        return IngestionStep.TMDB_OVERRIDES;
    }

    /**
     * Preenche os overrides do anime da pasta a partir do TMDB, numa execução nova do step.
     *
     * <p>Fluxo: pasta sem anime vinculado ou anime sem binding TMDB → {@code FAILED}; senão
     * preenche os overrides ({@code AnimeTmdbOverridesFillService#fillOverrides}, com retry de
     * rate limit), grava um evento {@code WARNING} por campo sem opção no TMDB nos idiomas
     * configurados — sem bloquear — e termina {@code COMPLETED}.
     *
     * @param task task em execução, com a pasta já carregada
     * @return status final do step; qualquer falha (TMDB fora, título inexistente no TMDB) vira
     *         {@code FAILED} com evento {@code ERROR}, sem lançar
     */
    @Override
    public IngestionStepStatus execute(IngestionTask task) {
        final StepRun run = start(task.id);

        IngestionStepStatus status;

        try {
            status = fillOverrides(run, task.folder);
        } catch (RuntimeException e) {
            Log.warnf(e, "Overrides TMDB da pasta %d (%s) falharam", task.folder.id, task.folder.name);

            run.error(e.getMessage());

            status = IngestionStepStatus.FAILED;
        }

        run.finish(status);

        return status;
    }

    private IngestionStepStatus fillOverrides(StepRun run, MediaFolder folder) {
        run.started("Buscando no TMDB o título, a sinopse e as imagens do anime");

        final Optional<Anime> boundAnime = animeService.findByFolder(folder.id);

        if (boundAnime.isEmpty()) {
            run.error("A pasta ainda não tem um anime da AniList vinculado");

            return IngestionStepStatus.FAILED;
        }

        final Anime anime = boundAnime.get();

        if (anime.tmdbId == null) {
            run.error(
                    "O anime ainda não está vinculado ao TMDB",
                    Map.of("anilistId", anime.anilistId)
            );

            return IngestionStepStatus.FAILED;
        }

        run.requestSent(
                "Buscando as traduções e as imagens no TMDB",
                Map.of("tmdbId", anime.tmdbId, "mediaType", anime.tmdbMediaType)
        );

        final TmdbOverridesFill fill = RetryUtils.withRateLimitRetry(
                () -> animeTmdbOverridesFillService.fillOverrides(anime.anilistId)
        );

        for (String field : fill.notFound()) {
            run.warning(
                    "O TMDB não tem %s nos idiomas configurados".formatted(
                            FIELD_NAMES.getOrDefault(field, field)
                    ),
                    Map.of("field", field)
            );
        }

        run.completed(
                "Título, sinopse e imagens atualizados a partir do TMDB",
                Map.of("overrides", fill)
        );

        return IngestionStepStatus.COMPLETED;
    }
}
