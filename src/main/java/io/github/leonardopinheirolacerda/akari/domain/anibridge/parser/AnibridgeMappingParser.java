package io.github.leonardopinheirolacerda.akari.domain.anibridge.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbMediaType;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.AnimeProvider;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.MappingSegment;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.parser.model.AnimeSourceDescriptor;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.parser.model.EpisodeRange;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.parser.model.TmdbTargetDescriptor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Parsing puro do {@code mappings.min.json} do anibridge — sem I/O de rede, só transforma o
 * stream já baixado em {@link MappingSegment} (ainda não persistidos, sem {@code id}).
 *
 * <p>O dataset é um dict aninhado {@code sourceDescriptor -> targetDescriptor -> sourceRange ->
 * targetRange}, não uma lista. O arquivo gira em torno de 9MB — carregar a árvore inteira com
 * {@link ObjectMapper#readTree(InputStream)} é tranquilo nesse tamanho, então a navegação é só
 * {@link JsonNode#fields()} normal, sem controle manual de token. São extraídos pares onde a
 * origem é {@code anilist:<id>} ou {@code mal:<id>} e o destino é {@code tmdb_show:<id>:s<n>}
 * ou {@code tmdb_movie:<id>} — o resto (chaves de metadata como {@code $meta}, outros providers
 * de origem/destino) é simplesmente ignorado ao iterar. A origem {@code mal:} existe porque o
 * dataset nem sempre liga o mesmo TMDB tanto por {@code anilist:} quanto por {@code mal:} pro
 * mesmo anime — persistir as duas cobre o caso em que só uma delas tem o bind; quem consumir
 * decide o fallback (anilist primeiro, mal se não achar nada).
 *
 * <p>Entradas com target range multi-segmento (ex.: {@code "1-6,8-13"}, vírgula) são
 * descartadas — cobrem só ~0.1% do dataset e exigiriam fatiar um único source range em vários
 * segments proporcionalmente. O sufixo {@code |ratio} é só armazenado em
 * {@link MappingSegment#ratio}, sem aplicar a conversão aqui — isso é responsabilidade de quem
 * consumir o segment depois.
 */
@ApplicationScoped
public class AnibridgeMappingParser {

    private static final String ANILIST_PREFIX = "anilist:";
    private static final String MAL_PREFIX = "mal:";
    private static final String TMDB_SHOW_PREFIX = "tmdb_show:";
    private static final String TMDB_MOVIE_PREFIX = "tmdb_movie:";

    @Inject
    ObjectMapper objectMapper;

    /**
     * Converte o {@code mappings.min.json} já baixado em segments.
     *
     * @param mappingsStream stream do arquivo já baixado — não é fechado por este método, quem
     *                        chama é responsável pelo ciclo de vida do arquivo temporário
     * @return os segments extraídos, ainda não persistidos
     */
    public List<MappingSegment> parse(InputStream mappingsStream) {
        final List<MappingSegment> segments = new ArrayList<>();

        final JsonNode root;
        try {
            root = objectMapper.readTree(mappingsStream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        final Iterator<Map.Entry<String, JsonNode>> sourceEntries = root.fields();
        while (sourceEntries.hasNext()) {
            final Map.Entry<String, JsonNode> sourceEntry = sourceEntries.next();

            final String key = sourceEntry.getKey();
            final JsonNode value = sourceEntry.getValue();

            final AnimeSourceDescriptor animeSource = parseAnimeSource(key);

            if (animeSource == null) {
                continue;
            }

            parseTargets(value, animeSource, segments);
        }

        return segments;
    }

    private void parseTargets(JsonNode targets, AnimeSourceDescriptor animeSource, List<MappingSegment> segments) {
        final Iterator<Map.Entry<String, JsonNode>> targetEntries = targets.fields();

        while (targetEntries.hasNext()) {
            final Map.Entry<String, JsonNode> targetEntry = targetEntries.next();

            final TmdbTargetDescriptor tmdbTarget = parseTmdbTarget(targetEntry.getKey());

            if (tmdbTarget == null) {
                continue;
            }

            parseRanges(targetEntry.getValue(), animeSource, tmdbTarget, segments);
        }
    }

    private void parseRanges(JsonNode ranges,
                             AnimeSourceDescriptor animeSource,
                             TmdbTargetDescriptor tmdbTarget,
                             List<MappingSegment> segments) {

        final Iterator<Map.Entry<String, JsonNode>> rangeEntries = ranges.fields();

        while (rangeEntries.hasNext()) {

            final Map.Entry<String, JsonNode> rangeEntry = rangeEntries.next();
            final JsonNode targetRangeNode = rangeEntry.getValue();

            if (!targetRangeNode.isTextual()) {
                continue;
            }

            final String targetRangeText = targetRangeNode.asText();
            if (targetRangeText.indexOf(',') >= 0) {
                continue;
            }

            final MappingSegment segment =
                    buildSegment(animeSource, tmdbTarget, rangeEntry.getKey(), targetRangeText);
            if (segment != null) {
                segments.add(segment);
            }
        }
    }

    private static AnimeSourceDescriptor parseAnimeSource(String descriptor) {
        if (descriptor.startsWith(ANILIST_PREFIX)) {
            final String anilistId = descriptor.substring(ANILIST_PREFIX.length());
            final Integer animeId = toIntOrNull(anilistId);

            if(animeId == null) {
                return null;
            }

            return new AnimeSourceDescriptor(AnimeProvider.ANILIST, animeId);
        }

        if (descriptor.startsWith(MAL_PREFIX)) {
            final String myanimelistId = descriptor.substring(MAL_PREFIX.length());
            final Integer animeId = toIntOrNull(myanimelistId);

            if(animeId == null) {
                return null;
            }

            return new AnimeSourceDescriptor(AnimeProvider.MAL, animeId);
        }

        return null;
    }

    private static TmdbTargetDescriptor parseTmdbTarget(String descriptor) {
        if (descriptor.startsWith(TMDB_MOVIE_PREFIX)) {
            final String tmdbMovieId = descriptor.substring(TMDB_MOVIE_PREFIX.length());
            final Integer tmdbId = toIntOrNull(tmdbMovieId);

            if (tmdbId == null) {
                return null;
            }

            return new TmdbTargetDescriptor(tmdbId, TmdbMediaType.MOVIE, null);
        }

        if (descriptor.startsWith(TMDB_SHOW_PREFIX)) {
            final String remainder = descriptor.substring(TMDB_SHOW_PREFIX.length());

            final int scopeIndex = remainder.indexOf(':');

            if (scopeIndex < 0) {
                return null;
            }

            final Integer tmdbId = toIntOrNull(remainder.substring(0, scopeIndex));
            final String scope = remainder.substring(scopeIndex + 1);

            // confirmado contra o dataset real (v3): das 28923 entradas tmdb_show, 100% têm scope
            // no formato s<número> — nenhuma sem scope, nenhuma em outro formato. Ex.: a chave
            // "tmdb_show:30991:s1" chega aqui como remainder = "30991:s1", scope = "s1".
            if (tmdbId == null || !scope.startsWith("s")) {
                return null;
            }

            final Integer seasonNumber = toIntOrNull(scope.substring(1));

            if (seasonNumber == null) {
                return null;
            }

            return new TmdbTargetDescriptor(tmdbId, TmdbMediaType.TV, seasonNumber);
        }

        return null;
    }

    private static MappingSegment buildSegment(AnimeSourceDescriptor animeSource,
                                               TmdbTargetDescriptor tmdbTarget,
                                               String sourceRangeText,
                                               String targetRangeText) {

        final int pipeIndex = targetRangeText.indexOf('|');

        final String rangeText = pipeIndex < 0
                ? targetRangeText
                : targetRangeText.substring(0, pipeIndex);

        final Integer ratio = pipeIndex < 0
                ? null
                : toIntOrNull(targetRangeText.substring(pipeIndex + 1));

        final EpisodeRange animeRange = parseRange(sourceRangeText);
        final EpisodeRange tmdbRange = parseRange(rangeText);

        if (animeRange == null || tmdbRange == null) {
            return null;
        }

        final MappingSegment segment = new MappingSegment();

        segment.animeProvider = animeSource.provider();
        segment.animeId = animeSource.animeId();
        segment.tmdbMediaType = tmdbTarget.mediaType();
        segment.tmdbId = tmdbTarget.tmdbId();
        segment.seasonNumber = tmdbTarget.seasonNumber();
        segment.animeEpisodeStart = animeRange.start();
        segment.animeEpisodeEnd = animeRange.end();
        segment.tmdbEpisodeStart = tmdbRange.start();
        segment.tmdbEpisodeEnd = tmdbRange.end();
        segment.ratio = ratio;

        return segment;
    }

    private static EpisodeRange parseRange(String text) {

        if (text.endsWith("-")) {
            final String startStr = text.substring(0, text.length() - 1);
            final Integer start = toIntOrNull(startStr);

            return start == null
                    ? null
                    : new EpisodeRange(start, null);
        }

        final int dashIndex = text.indexOf('-');

        if (dashIndex < 0) {
            final Integer value = toIntOrNull(text);

            return value == null
                    ? null
                    : new EpisodeRange(value, value);
        }

        final String startStr = text.substring(0, dashIndex);
        final String endStr = text.substring(dashIndex + 1);

        final Integer start = toIntOrNull(startStr);
        final Integer end = toIntOrNull(endStr);

        if (start == null || end == null) {
            return null;
        }
        return new EpisodeRange(start, end);
    }

    private static Integer toIntOrNull(String text) {
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
