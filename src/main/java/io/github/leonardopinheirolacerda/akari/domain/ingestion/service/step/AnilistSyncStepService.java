package io.github.leonardopinheirolacerda.akari.domain.ingestion.service.step;

import io.github.leonardopinheirolacerda.akari.api.dto.AnilistMediaSummary;
import io.github.leonardopinheirolacerda.akari.api.dto.AnilistTitle;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeBindingRequest;
import io.github.leonardopinheirolacerda.akari.domain.anilist.service.AnilistSearchService;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeService;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.IngestionStepExecution;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.IngestionTask;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums.IngestionEventType;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums.IngestionStep;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums.IngestionStepStatus;
import io.github.leonardopinheirolacerda.akari.domain.mediafolder.model.MediaFolder;
import io.github.leonardopinheirolacerda.akari.domain.videofile.service.VideoFileService;
import io.github.leonardopinheirolacerda.akari.utils.RetryUtils;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Step {@code ANILIST_SYNC} da ingestão: vincula a pasta da task a um anime da AniList, buscando
 * pelo nome da pasta.
 *
 * <p>Não conhece os demais steps: registra a própria execução e seus eventos pelos métodos
 * herdados de {@link IngestionStepService} e devolve o status final. Não tem
 * {@code @Transactional} próprio — cada chamada a outro service abre a sua transação, sem segurar
 * uma aberta durante as chamadas à AniList, que podem demorar sob rate limit
 * ({@link RetryUtils}).
 */
@ApplicationScoped
public class AnilistSyncStepService extends IngestionStepService {

    private static final int SEARCH_PAGE_SIZE = 5;

    // Caracteres proibidos em nome de pasta, mais o hífen, usado no lugar da barra (Fate-stay).
    private static final Pattern IGNORED_CHARACTERS = Pattern.compile("[/\\\\:*?\"<>|\\-]");

    private static final Pattern WHITESPACES = Pattern.compile("\\s+");

    @Inject
    AnimeService animeService;

    @Inject
    AnilistSearchService anilistSearchService;

    @Inject
    VideoFileService videoFileService;

    /**
     * Identifica este step como {@code ANILIST_SYNC}.
     *
     * @return {@link IngestionStep#ANILIST_SYNC}
     */
    @Override
    protected IngestionStep step() {
        return IngestionStep.ANILIST_SYNC;
    }

    /**
     * Vincula a pasta da task a um anime da AniList, numa execução nova do step.
     *
     * <p>Fluxo: pasta já vinculada → mantém o vínculo ({@code COMPLETED}); senão busca o nome da
     * pasta na AniList e procura, entre os resultados, um nome igual ao da pasta, sem diferenciar maiúsculas de minúsculas, nesta
     * prioridade, cada nível olhando todos os resultados: título (romaji, inglês, nativo,
     * preferido) exato; título ignorando os caracteres proibidos em nome de pasta
     * ({@code / \ : * ? " < > |}) e o hífen; sinônimo exato; sinônimo com a mesma normalização —
     * um título que bate sempre ganha de um sinônimo que bate; dentro de um nível, desempata pelo
     * número de episódios igual à quantidade de arquivos da pasta, e se o empate continuar, para
     * à espera de curadoria ({@code WAITING_CURATION}). Achou → vincula ({@code COMPLETED});
     * não achou → para à espera de curadoria ({@code WAITING_CURATION}); achou, mas o anime já
     * está sincronizado com outra pasta → evento {@code ERROR} ({@code FAILED}). Quando o número
     * de episódios do anime é conhecido e difere da quantidade de arquivos, grava um evento
     * {@code WARNING} e vincula assim mesmo. Busca e bind são retentados em rate limit.
     *
     * @param task task em execução, com a pasta já carregada
     * @return status final do step; qualquer falha (AniList fora, anime já sincronizado, bind
     *         recusado porque outra task vinculou o mesmo título antes) vira {@code FAILED} com
     *         evento {@code ERROR}, sem lançar
     */
    @Override
    public IngestionStepStatus execute(IngestionTask task) {
        final IngestionStepExecution stepExecution = start(task.id);

        IngestionStepStatus status;

        try {
            status = bindFolder(stepExecution, task.folder);
        } catch (RuntimeException e) {
            Log.warnf(e, "Vínculo AniList da pasta %d (%s) falhou", task.folder.id, task.folder.name);

            record(
                    stepExecution,
                    IngestionEventType.ERROR,
                    e.getMessage(),
                    null
            );

            status = IngestionStepStatus.FAILED;
        }

        finish(stepExecution, status);

        return status;
    }

    private IngestionStepStatus bindFolder(IngestionStepExecution stepExecution, MediaFolder folder) {
        final Integer folderId = folder.id;
        final String folderName = folder.name;

        record(
                stepExecution,
                IngestionEventType.STARTED,
                "Vínculo da pasta com a AniList iniciado",
                null
        );

        final Optional<Anime> boundAnime = animeService.findByFolder(folderId);

        if (boundAnime.isPresent()) {
            record(
                    stepExecution,
                    IngestionEventType.MATCH_SELECTED,
                    "Pasta já vinculada a um anime",
                    Map.of("anilistId", boundAnime.get().anilistId)
            );
            record(
                    stepExecution,
                    IngestionEventType.COMPLETED,
                    "Vínculo com a AniList mantido",
                    null
            );

            return IngestionStepStatus.COMPLETED;
        }

        final long fileCount = videoFileService.countByFolder(folderId);

        record(
                stepExecution,
                IngestionEventType.REQUEST_SENT,
                "Busca enviada à AniList",
                Map.of("query", folderName, "page", 0, "size", SEARCH_PAGE_SIZE)
        );

        final List<AnilistMediaSummary> results = RetryUtils.withRateLimitRetry(
                () -> anilistSearchService.search(folderName, 0, SEARCH_PAGE_SIZE, false)
        ).getData();

        record(
                stepExecution,
                IngestionEventType.RESPONSE_RECEIVED,
                "Resposta da AniList recebida",
                Map.of("results", results.size())
        );

        if (!results.isEmpty()) {
            record(
                    stepExecution,
                    IngestionEventType.RESULTS_FOUND,
                    "%d candidatos encontrados na AniList".formatted(results.size()),
                    Map.of("candidates", candidatesOf(results))
            );
        }

        final String normalizedFolderName = normalize(folderName);

        final Predicate<String> exact = name -> name.equalsIgnoreCase(folderName);
        final Predicate<String> normalized = name -> normalize(name).equals(normalizedFolderName);

        final List<AnilistMediaSummary> matches = findByName(results, this::titlesOf, exact)
                .or(() -> findByName(results, this::titlesOf, normalized))
                .or(() -> findByName(results, this::synonymsOf, exact))
                .or(() -> findByName(results, this::synonymsOf, normalized))
                .orElse(List.of());

        if (matches.isEmpty()) {
            record(
                    stepExecution,
                    IngestionEventType.MATCH_NOT_FOUND,
                    "Nenhum título da AniList tem nome igual ao da pasta",
                    null
            );

            return IngestionStepStatus.WAITING_CURATION;
        }

        final Optional<AnilistMediaSummary> match = untie(matches, fileCount);

        if (match.isEmpty()) {
            record(
                    stepExecution,
                    IngestionEventType.MATCH_NOT_FOUND,
                    "Mais de um título da AniList tem nome igual ao da pasta, sem como desempatar",
                    Map.of("candidates", candidatesOf(matches), "files", fileCount)
            );

            return IngestionStepStatus.WAITING_CURATION;
        }

        final Integer anilistId = match.get().getAnilistId();

        record(
                stepExecution,
                IngestionEventType.MATCH_SELECTED,
                "Título da AniList com nome igual ao da pasta encontrado",
                Map.of("anilistId", anilistId, "title", titleOf(match.get()))
        );

        if (animeService.find(anilistId).isPresent()) {
            record(
                    stepExecution,
                    IngestionEventType.ERROR,
                    "O anime encontrado já está sincronizado com outra pasta",
                    Map.of("anilistId", anilistId)
            );

            return IngestionStepStatus.FAILED;
        }

        final Integer episodes = match.get().getEpisodes();

        if (episodes != null && episodes != fileCount) {
            record(
                    stepExecution,
                    IngestionEventType.WARNING,
                    "Quantidade de arquivos da pasta diferente do número de episódios do anime",
                    Map.of("anilistId", anilistId, "episodes", episodes, "files", fileCount)
            );
        }

        RetryUtils.withRateLimitRetry(
                () -> {
                    final AnimeBindingRequest animeBindingRequest = new AnimeBindingRequest();
                    animeBindingRequest.setFolderId(folderId);

                    return animeService.bindAnime(anilistId, animeBindingRequest);
                }
        );

        record(
                stepExecution,
                IngestionEventType.COMPLETED,
                "Pasta vinculada ao anime da AniList",
                Map.of("anilistId", anilistId)
        );

        return IngestionStepStatus.COMPLETED;
    }

    private List<Map<String, Object>> candidatesOf(List<AnilistMediaSummary> summaries) {
        return summaries.stream()
                .map(summary -> Map.<String, Object>of("anilistId", summary.getAnilistId(), "title", titleOf(summary)))
                .toList();
    }

    /**
     * Resultados da busca com algum dos nomes extraídos por {@code names} que satisfaz o
     * critério, na ordem da AniList; vazio quando nenhum bate, pra que o próximo nível seja
     * tentado.
     */
    private Optional<List<AnilistMediaSummary>> findByName(
            List<AnilistMediaSummary> results,
            Function<AnilistMediaSummary, Stream<String>> names,
            Predicate<String> criteria
    ) {
        final List<AnilistMediaSummary> matches = results.stream()
                .filter(summary -> names.apply(summary).anyMatch(criteria))
                .toList();

        return matches.isEmpty() ? Optional.empty() : Optional.of(matches);
    }

    /**
     * Escolhe um entre os resultados que bateram: o único, se só um bateu; senão, o único com
     * número de episódios igual à quantidade de arquivos da pasta. Vazio quando o empate
     * continua — nenhum ou mais de um com esse número de episódios.
     */
    private Optional<AnilistMediaSummary> untie(List<AnilistMediaSummary> matches, long fileCount) {
        if (matches.size() == 1) {
            return Optional.of(matches.getFirst());
        }

        final List<AnilistMediaSummary> sameEpisodes = matches.stream()
                .filter(summary -> summary.getEpisodes() != null && summary.getEpisodes() == fileCount)
                .toList();

        if (sameEpisodes.size() == 1) {
            return Optional.of(sameEpisodes.getFirst());
        }

        return Optional.empty();
    }

    private Stream<String> titlesOf(AnilistMediaSummary summary) {
        final AnilistTitle title = summary.getTitle();

        if (title == null) {
            return Stream.empty();
        }

        return Stream.of(
                        title.getRomaji(),
                        title.getEnglish(),
                        title.getNativeTitle(),
                        title.getUserPreferred()
                )
                .filter(Objects::nonNull);
    }

    private Stream<String> synonymsOf(AnilistMediaSummary summary) {
        return summary.getSynonyms()
                .stream()
                .filter(Objects::nonNull);
    }

    /**
     * Troca os caracteres proibidos em nome de pasta e o hífen por espaço, junta os espaços e passa
     * pra minúsculas — o resto da pontuação ({@code !}, {@code '}, {@code ,}) continua exigindo
     * igualdade exata, pra não juntar títulos como {@code K-ON!} e {@code K-ON!!}. Troca por
     * espaço, não remove, porque o nome da pasta costuma ter espaço onde o título tem o caractere
     * proibido ({@code Re Zero} pra {@code Re:Zero}); o hífen entra porque é o outro substituto
     * usado pra barra ({@code Fate-stay} pra {@code Fate/stay}).
     */
    private String normalize(String name) {
        final String withoutIgnored = IGNORED_CHARACTERS.matcher(name)
                .replaceAll(" ");

        return WHITESPACES.matcher(withoutIgnored)
                .replaceAll(" ")
                .strip()
                .toLowerCase(Locale.ROOT);
    }

    private String titleOf(AnilistMediaSummary summary) {
        final AnilistTitle title = summary.getTitle();

        if (title == null) {
            return "";
        }

        return Stream.of(title.getUserPreferred(), title.getRomaji(), title.getEnglish())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("");
    }
}
