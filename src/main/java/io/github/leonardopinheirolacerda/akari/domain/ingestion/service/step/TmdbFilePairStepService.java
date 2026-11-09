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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    private static final String OUT_OF_AIR_WINDOW = "OUT_OF_AIR_WINDOW";

    // Em que ponto do pareamento o arquivo parou, por motivo (status da resolução ou
    // EPISODE_CONFLICT) — o pareamento tenta o mapeamento do anibridge e, no que faltar, a data
    // de exibição do anime.
    private static final Map<String, String> UNPAIRED_REASONS = Map.of(
            "NO_EPISODE_NUMBER",
            "não foi possível identificar o número do episódio no nome do arquivo",
            "NOT_MAPPED",
            "o episódio não foi encontrado nem pelo mapeamento do anibridge nem pela data de "
                    + "exibição",
            "AMBIGUOUS_ABSOLUTE_NUMBERING",
            "o arquivo usa a numeração contínua da franquia, e o mapeamento do anibridge não "
                    + "permite convertê-la com segurança",
            "NOT_FOUND_IN_TMDB",
            "o episódio indicado pelo anibridge não existe no TMDB",
            OUT_OF_AIR_WINDOW,
            "o episódio indicado foi exibido fora do período deste anime, provavelmente em outra "
                    + "temporada",
            "NO_MAPPING",
            "o anibridge não tem este anime mapeado, e a data de exibição também não encontrou o "
                    + "episódio",
            "CONFLICTING_MAPPING",
            "o mapeamento do anibridge para este anime é inconsistente, e a data de exibição "
                    + "também não encontrou o episódio",
            "EPISODE_CONFLICT",
            "outro arquivo da pasta corresponde ao mesmo episódio, e nenhum dos dois foi "
                    + "pareado"
    );

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
        run.started("Pareando os arquivos da pasta com os episódios do TMDB");

        final Optional<Anime> boundAnime = animeService.findByFolder(folder.id);

        if (boundAnime.isEmpty()) {
            run.error("A pasta ainda não tem um anime da AniList vinculado");

            return IngestionStepStatus.FAILED;
        }

        final Anime anime = boundAnime.get();

        if (anime.tmdbId == null) {
            run.error(
                    "O anime ainda não está vinculado a uma série do TMDB",
                    Map.of("anilistId", anime.anilistId)
            );

            return IngestionStepStatus.FAILED;
        }

        if (anime.tmdbMediaType == TmdbMediaType.MOVIE) {
            run.skipped("O anime está vinculado a um filme, que não tem episódios para parear");

            return IngestionStepStatus.SKIPPED;
        }

        run.requestSent(
                "Identificando o episódio de cada arquivo: primeiro pelo mapeamento do "
                        + "anibridge e, para o que faltar, pela data de exibição do anime",
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
                resultsMessage(fill),
                Map.of(
                        "paired", fill.paired(),
                        "kept", fill.kept(),
                        "unpaired", fill.unpaired().size()
                )
        );

        for (UnpairedVideoFile file : fill.unpaired()) {
            run.warning(unpairedMessage(file), unpairedDetails(file, anime));
        }

        if (fill.paired() + fill.kept() == 0) {
            run.matchNotFound(
                    "Nenhum arquivo pôde ser pareado automaticamente. "
                            + "Faça o pareamento manualmente"
            );

            return IngestionStepStatus.UNRESOLVED;
        }

        run.completed(
                "Pareamento dos episódios concluído",
                Map.of("paired", fill.paired(), "kept", fill.kept())
        );

        return IngestionStepStatus.COMPLETED;
    }

    /**
     * Resumo do pareamento da pasta, só com as contagens que não são zero (ex.: "10 arquivos
     * pareados e 2 sem par").
     */
    private static String resultsMessage(TmdbFilePairingFill fill) {
        final List<String> parts = new ArrayList<>();

        if (fill.paired() > 0) {
            parts.add(plural(fill.paired(), "arquivo pareado", "arquivos pareados"));
        }

        if (fill.kept() > 0) {
            parts.add(plural(fill.kept(), "já tinha par", "já tinham par"));
        }

        if (!fill.unpaired().isEmpty()) {
            parts.add(fill.unpaired().size() + " sem par");
        }

        if (parts.size() == 1) {
            return parts.getFirst();
        }

        return String.join(", ", parts.subList(0, parts.size() - 1)) + " e " + parts.getLast();
    }

    /** Quantidade seguida da forma no singular ou no plural (ex.: "1 arquivo", "2 arquivos"). */
    private static String plural(Integer count, String singular, String plural) {
        if (count == 1) {
            return "1 " + singular;
        }

        return count + " " + plural;
    }

    /**
     * Mensagem de um arquivo sem par: o nome do arquivo e em que ponto do pareamento ele parou.
     */
    private static String unpairedMessage(UnpairedVideoFile file) {
        final String reason = UNPAIRED_REASONS.getOrDefault(
                file.reason(),
                "não foi possível identificar o episódio"
        );

        return file.fileName() + ": " + reason;
    }

    /**
     * Detalhes de um arquivo sem par; no episódio exibido fora do período, também o período do
     * anime na AniList, pra comparar com a data do episódio no TMDB.
     */
    private static Map<String, Object> unpairedDetails(UnpairedVideoFile file, Anime anime) {
        final Map<String, Object> details = new LinkedHashMap<>();

        details.put("file", file);

        if (OUT_OF_AIR_WINDOW.equals(file.reason())) {
            details.put("animeStartDate", anime.startDate);
            details.put("animeEndDate", anime.endDate);
        }

        return details;
    }
}
