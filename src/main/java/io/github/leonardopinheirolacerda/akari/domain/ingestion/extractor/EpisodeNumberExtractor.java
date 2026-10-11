package io.github.leonardopinheirolacerda.akari.domain.ingestion.extractor;

import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai o número de episódio do nome de um arquivo de vídeo — best-effort, usado pela ingestão
 * pra tentar o pairing TMDB automático. Arquivo sem nenhum padrão reconhecido simplesmente não é
 * pareado (nunca bloqueia a ingestão).
 *
 * <p>Pipeline de 4 padrões, o primeiro que casar ganha: marcador {@code SxxExx} colado sem
 * separador (ex.: {@code "CANAAN.S01E01.mkv"}), número isolado por separador (ex.:
 * {@code "One Piece - 01.mkv"}), marcador {@code E}/{@code EP}/{@code Episode} solto (ex.:
 * {@code "Title E01.mkv"}), e por fim a última sequência de dígitos antes da extensão.
 */
public final class EpisodeNumberExtractor {

    private static final Pattern EXTENSION = Pattern.compile("\\.[^.]+$");

    private static final Pattern SEASON_EPISODE_MARKER = Pattern.compile("(?i)\\bS\\d{1,2}E(\\d{1,4})(?:v\\d)?\\b");

    private static final Pattern SEPARATED_NUMBER = Pattern.compile(
            "(?i)(?<!\\d)(?:^|[ ._\\[-])(\\d{1,4})(?!p\\b)(?!bit\\b)(?:v\\d)?(?=[ ._\\]-]|$)");

    private static final Pattern EPISODE_MARKER = Pattern.compile("(?i)\\bEP?\\.?\\s?(\\d{1,4})\\b");

    private static final Pattern TRAILING_NUMBER = Pattern.compile("(\\d{1,4})$");

    private EpisodeNumberExtractor() {
    }

    /**
     * Tenta extrair o número de episódio do nome do arquivo.
     *
     * @param fileName nome do arquivo, com extensão
     * @return o número do episódio, ou vazio se nenhum padrão bateu
     */
    public static OptionalInt extract(String fileName) {
        final String nameWithoutExtension = EXTENSION.matcher(fileName).replaceFirst("");

        final OptionalInt seasonEpisode = firstMatch(SEASON_EPISODE_MARKER, nameWithoutExtension);
        if (seasonEpisode.isPresent()) {
            return seasonEpisode;
        }

        final OptionalInt separated = firstMatch(SEPARATED_NUMBER, nameWithoutExtension);
        if (separated.isPresent()) {
            return separated;
        }

        final OptionalInt marked = firstMatch(EPISODE_MARKER, nameWithoutExtension);
        if (marked.isPresent()) {
            return marked;
        }

        return firstMatch(TRAILING_NUMBER, nameWithoutExtension);
    }

    private static OptionalInt firstMatch(Pattern pattern, String text) {
        final Matcher matcher = pattern.matcher(text);

        if (!matcher.find()) {
            return OptionalInt.empty();
        }

        try {
            return OptionalInt.of(Integer.parseInt(matcher.group(1)));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

}
