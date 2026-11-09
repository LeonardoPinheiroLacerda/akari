package io.github.leonardopinheirolacerda.akari.domain.ingestion.service.step;

import io.github.leonardopinheirolacerda.akari.api.dto.TmdbBindingDetails;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbBindingRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbMediaType;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.CandidateResolution;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.service.AnibridgeResolutionService;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeService;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeTmdbService;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.IngestionTask;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums.IngestionStep;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums.IngestionStepStatus;
import io.github.leonardopinheirolacerda.akari.domain.mediafolder.model.MediaFolder;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.github.leonardopinheirolacerda.akari.utils.RetryUtils;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.Optional;

/**
 * Step {@code TMDB_BIND} da ingestão: vincula o anime da pasta a uma série ou filme do TMDB, pelo
 * melhor candidato do anibridge — o mesmo critério da proposta de binding do portal.
 *
 * <p>Não conhece os demais steps: lê o anime vinculado à pasta (que o {@code ANILIST_SYNC} deixou)
 * e registra a própria execução e seus eventos pelo {@code StepRun} que o
 * {@link IngestionStepService#start} devolve. Não tem {@code @Transactional} próprio — cada
 * chamada a outro service abre a sua transação, sem segurar uma aberta durante a chamada ao TMDB.
 */
@ApplicationScoped
public class TmdbBindStepService extends IngestionStepService {

    @Inject
    AnimeService animeService;

    @Inject
    AnimeTmdbService animeTmdbService;

    @Inject
    AnibridgeResolutionService anibridgeResolutionService;

    /**
     * Identifica este step como {@code TMDB_BIND}.
     *
     * @return {@link IngestionStep#TMDB_BIND}
     */
    @Override
    protected IngestionStep step() {
        return IngestionStep.TMDB_BIND;
    }

    /**
     * Vincula o anime da pasta ao TMDB, numa execução nova do step.
     *
     * <p>Fluxo: pasta sem anime vinculado → {@code FAILED}; anime já vinculado ao TMDB → mantém
     * o vínculo ({@code COMPLETED}); senão escolhe o melhor candidato do anibridge pelo
     * {@code anilistId} (ou {@code malId}) e o total de episódios do anime. Para à espera de
     * curadoria ({@code UNRESOLVED}) sem candidato, quando há mais de um candidato e o
     * melhor é suspeito (sobreposição ou cobertura maior que o anime), quando o melhor empata
     * com outro em todos os critérios (suspeita, cobertura e tipo que combina com o formato do
     * anime), ou quando o título escolhido não existe no TMDB (dado do anibridge desatualizado,
     * conferido com {@code fetchDetails} antes do bind — a ficha fica em cache pro bind); senão
     * vincula ({@code COMPLETED}), com retry de rate limit nas chamadas ao TMDB. Com um único candidato, a
     * sobreposição não impede o bind: costuma ser o anibridge numerando os mesmos episódios de
     * dois jeitos na mesma série, o que só importa pro pairing de episódios.
     *
     * @param task task em execução, com a pasta já carregada
     * @return status final do step; qualquer outra falha (TMDB fora, bind recusado) vira
     *         {@code FAILED} com evento {@code ERROR}, sem lançar
     */
    @Override
    public IngestionStepStatus execute(IngestionTask task) {
        final StepRun run = start(task.id);

        IngestionStepStatus status;

        try {
            status = bindTmdb(run, task.folder);
        } catch (RuntimeException e) {
            Log.warnf(e, "Vínculo TMDB da pasta %d (%s) falhou", task.folder.id, task.folder.name);

            run.error(e.getMessage());

            status = IngestionStepStatus.FAILED;
        }

        run.finish(status);

        return status;
    }

    private IngestionStepStatus bindTmdb(StepRun run, MediaFolder folder) {
        run.started("Procurando no TMDB a série ou o filme deste anime");

        final Optional<Anime> boundAnime = animeService.findByFolder(folder.id);

        if (boundAnime.isEmpty()) {
            run.error("A pasta ainda não tem um anime da AniList vinculado");

            return IngestionStepStatus.FAILED;
        }

        final Anime anime = boundAnime.get();

        if (anime.tmdbId != null) {
            run.matchSelected(
                    "O anime já estava vinculado ao TMDB",
                    Map.of("tmdbId", anime.tmdbId, "mediaType", anime.tmdbMediaType)
            );
            run.completed("O vínculo com o TMDB foi mantido");

            return IngestionStepStatus.COMPLETED;
        }

        final CandidateResolution resolution = anibridgeResolutionService.resolveCandidates(
                anime.anilistId,
                anime.malId,
                anime.episodes,
                anime.format
        );

        if (resolution.best() == null) {
            run.matchNotFound(
                    "O anibridge não tem este anime mapeado no TMDB. Escolha o título manualmente",
                    Map.of("anilistId", anime.anilistId)
            );

            return IngestionStepStatus.UNRESOLVED;
        }

        final Integer tmdbId = resolution.best().tmdbId();
        final TmdbMediaType mediaType = resolution.best().mediaType();

        if (resolution.candidateCount() > 1 && resolution.bestSuspicious()) {
            run.matchNotFound(
                    "O título mais provável no anibridge cobre mais episódios que o anime ou se "
                            + "sobrepõe a outro. Escolha o título manualmente",
                    Map.of(
                            "tmdbId", tmdbId,
                            "mediaType", mediaType,
                            "candidates", resolution.candidateCount()
                    )
            );

            return IngestionStepStatus.UNRESOLVED;
        }

        if (resolution.tied()) {
            run.matchNotFound(
                    "O anibridge indica mais de um título do TMDB igualmente provável. "
                            + "Escolha o título manualmente",
                    Map.of(
                            "tmdbId", tmdbId,
                            "mediaType", mediaType,
                            "candidates", resolution.candidateCount()
                    )
            );

            return IngestionStepStatus.UNRESOLVED;
        }

        run.matchSelected(
                "Título do TMDB escolhido pelo mapeamento do anibridge",
                Map.of(
                        "tmdbId", tmdbId,
                        "mediaType", mediaType,
                        "candidates", resolution.candidateCount()
                )
        );

        final TmdbBindingDetails details;

        try {
            details = RetryUtils.withRateLimitRetry(
                    () -> animeTmdbService.fetchDetails(tmdbId, mediaType)
            );
        } catch (ResourceNotFoundException e) {
            run.matchNotFound(
                    "O título indicado pelo anibridge não existe mais no TMDB. "
                            + "Escolha o título manualmente",
                    Map.of("tmdbId", tmdbId, "mediaType", mediaType)
            );

            return IngestionStepStatus.UNRESOLVED;
        }

        RetryUtils.withRateLimitRetry(
                () -> {
                    final TmdbBindingRequest tmdbBindingRequest = new TmdbBindingRequest();
                    tmdbBindingRequest.setTmdbId(tmdbId);
                    tmdbBindingRequest.setMediaType(mediaType);

                    return animeTmdbService.bindTmdb(anime.anilistId, tmdbBindingRequest);
                }
        );

        run.completed(
                "Anime vinculado a \"%s\" no TMDB".formatted(details.getTitle()),
                Map.of("tmdbId", tmdbId, "mediaType", mediaType)
        );

        return IngestionStepStatus.COMPLETED;
    }
}
