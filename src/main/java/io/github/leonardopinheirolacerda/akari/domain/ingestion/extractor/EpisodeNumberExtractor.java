package io.github.leonardopinheirolacerda.akari.domain.ingestion.extractor;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Extrai o número de episódio do nome de um arquivo de vídeo — best-effort, usado pela resolução
 * do pairing TMDB ({@code EpisodePairingResolutionService}) e pela chave de ordenação natural dos
 * arquivos ({@code VideoFileService}). Arquivo sem nenhum padrão reconhecido simplesmente fica sem
 * número.
 *
 * <p>Pipeline de 4 padrões, o primeiro que casar ganha: marcador {@code SxxExx} colado sem
 * separador (ex.: {@code "CANAAN.S01E01.mkv"}), número isolado por separador (ex.:
 * {@code "One Piece - 01.mkv"}), marcador {@code E}/{@code EP}/{@code Episode} solto (ex.:
 * {@code "Title E01.mkv"}), e por fim a última sequência de dígitos antes da extensão.
 *
 * <p>Dentro do número isolado por separador, quando o título tem um número embutido antes do
 * episódio (ex.: {@code "Mob Psycho 100 - 01.mkv"}, onde "100" é parte do nome), prefere o
 * <strong>último</strong> número precedido por hífen ({@code "- 01"}) — convenção quase
 * universal de fansub pra separar título de episódio. Último, não primeiro: títulos com
 * subtítulo também hifenado (ex.: {@code "Fairy Tail - 100 Years Quest - 01.mkv"}) têm mais de
 * um candidato precedido por hífen, e o episódio é sempre o mais próximo da extensão. Sem
 * nenhum candidato com hífen, cai pro primeiro número isolado encontrado (comportamento de
 * sempre). Validado contra os ~10 mil arquivos reais da biblioteca: zero regressão, ~430
 * extrações corrigidas.
 *
 * <p>{@link #extractForFolder} complementa o {@link #extract} olhando a pasta inteira, só quando
 * o arquivo por arquivo devolve o mesmo número em mais de um arquivo — sinal de que o extrator
 * pegou um número fixo do nome (temporada, número do título) em vez do episódio.
 */
public final class EpisodeNumberExtractor {

    private static final Pattern EXTENSION = Pattern.compile("\\.[^.]+$");

    private static final Pattern SEASON_EPISODE_MARKER = Pattern.compile("(?i)\\bS\\d{1,2}E(\\d{1,4})(?:v\\d)?\\b");

    private static final Pattern SEPARATED_NUMBER = Pattern.compile(
            "(?i)(?<!\\d)(?:^|[ ._\\[-])(\\d{1,4})(?!p\\b)(?!bit\\b)(?:v\\d)?(?=[ ._\\]-]|$)");

    private static final Pattern EPISODE_MARKER = Pattern.compile("(?i)\\bEP?\\.?\\s?(\\d{1,4})\\b");

    private static final Pattern TRAILING_NUMBER = Pattern.compile("(\\d{1,4})$");

    private static final Pattern NATURAL_CHUNK = Pattern.compile("\\d+|\\D+");

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

        final OptionalInt separated = lastHyphenPrecededOrFirstMatch(SEPARATED_NUMBER, nameWithoutExtension);
        if (separated.isPresent()) {
            return separated;
        }

        final OptionalInt marked = firstMatch(EPISODE_MARKER, nameWithoutExtension);
        if (marked.isPresent()) {
            return marked;
        }

        return firstMatch(TRAILING_NUMBER, nameWithoutExtension);
    }

    /**
     * Extrai o número de episódio de cada arquivo de uma pasta, olhando a pasta inteira.
     *
     * <p>Usa o {@link #extract} de cada arquivo, a não ser que ele devolva o mesmo número em mais
     * de um arquivo (ex.: {@code "Season 1 Episode 01"} e {@code "Season 1 Episode 02"} saem
     * ambos 1, a temporada). Nesse caso, tenta, nesta ordem:
     * <ol>
     *   <li>o número que varia entre os arquivos com número repetido: todos esses nomes têm a
     *       mesma quantidade de números isolados por separador, e exatamente uma posição tem um
     *       valor diferente em cada um — ela é o episódio. Os arquivos com número único mantêm
     *       o do {@link #extract} (ex.: pasta que mistura {@code "OVA - 08"} de um lançamento
     *       com {@code "2_Ova 01"} de outro), desde que nenhum número se repita no fim;</li>
     *   <li>a ordem dos nomes (o primeiro é o episódio 1), comparando trechos numéricos como
     *       números, só quando a pasta tem exatamente {@code expectedEpisodes} arquivos e todos
     *       os nomes têm a mesma quantidade de números isolados — sem essas travas, um arquivo
     *       extra (OVA, abertura, {@code v2}) ou um lançamento de outro grupo deslocaria os
     *       seguintes sem aviso.</li>
     * </ol>
     * Se nenhuma das duas se aplica, devolve o resultado arquivo por arquivo. Nenhuma das duas
     * depende da ordem em que os nomes chegam: a ordem natural dos arquivos de vídeo também sai
     * do {@link #extract}, e é justamente nessas pastas que ela fica embaralhada.
     *
     * @param fileNames nomes dos arquivos da pasta, com extensão
     * @param expectedEpisodes total de episódios do anime; {@code null} se desconhecido
     * @return um número por arquivo, na mesma ordem de {@code fileNames}
     */
    public static List<OptionalInt> extractForFolder(List<String> fileNames, Integer expectedEpisodes) {
        final List<OptionalInt> perFile = fileNames.stream()
                .map(EpisodeNumberExtractor::extract)
                .toList();

        final List<Integer> repeatedIndexes = repeatedIndexes(perFile);

        if (repeatedIndexes.isEmpty()) {
            return perFile;
        }

        final Optional<List<OptionalInt>> varying = replaceRepeatedByVaryingNumbers(
                fileNames,
                perFile,
                repeatedIndexes
        );

        if (varying.isPresent()) {
            return varying.get();
        }

        final List<List<Integer>> numbersByFile = fileNames.stream()
                .map(EpisodeNumberExtractor::separatedNumbers)
                .toList();

        if (expectedEpisodes != null
                && fileNames.size() == expectedEpisodes
                && haveSameNumberCount(numbersByFile)) {

            return numberByNameOrder(fileNames);
        }

        return perFile;
    }

    /** Posições dos arquivos cujo número extraído aparece em mais de um arquivo. */
    private static List<Integer> repeatedIndexes(List<OptionalInt> numbers) {
        final Map<Integer, Long> occurrences = numbers.stream()
                .filter(OptionalInt::isPresent)
                .map(OptionalInt::getAsInt)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        return IntStream.range(0, numbers.size())
                .filter(index -> numbers.get(index).isPresent())
                .filter(index -> occurrences.get(numbers.get(index).getAsInt()) > 1)
                .boxed()
                .toList();
    }

    /**
     * Troca o número dos arquivos com número repetido pelo da única posição, entre os números
     * isolados de cada um desses nomes, com um valor diferente em cada arquivo. Vazio se esses
     * nomes não têm a mesma quantidade de números, se nenhuma, ou mais de uma, posição varia
     * assim, ou se algum número ainda se repete na pasta depois da troca.
     */
    private static Optional<List<OptionalInt>> replaceRepeatedByVaryingNumbers(List<String> fileNames,
                                                                             List<OptionalInt> perFile,
                                                                             List<Integer> repeatedIndexes) {

        final List<List<Integer>> numbersByFile = repeatedIndexes.stream()
                .map(index -> separatedNumbers(fileNames.get(index)))
                .toList();

        if (!haveSameNumberCount(numbersByFile)) {
            return Optional.empty();
        }

        final int count = numbersByFile.getFirst()
                .size();

        final List<Integer> varyingPositions = IntStream.range(0, count)
                .filter(position -> isDistinctPerFile(numbersByFile, position))
                .boxed()
                .toList();

        if (varyingPositions.size() != 1) {
            return Optional.empty();
        }

        final int position = varyingPositions.getFirst();
        final List<OptionalInt> replaced = new ArrayList<>(perFile);

        for (int i = 0; i < repeatedIndexes.size(); i++) {
            replaced.set(repeatedIndexes.get(i), OptionalInt.of(numbersByFile.get(i).get(position)));
        }

        if (!repeatedIndexes(replaced).isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(replaced);
    }

    /** Todos os nomes têm a mesma quantidade de números isolados, e ao menos um. */
    private static boolean haveSameNumberCount(List<List<Integer>> numbersByFile) {
        final int count = numbersByFile.getFirst()
                .size();

        return count > 0 && numbersByFile.stream().allMatch(numbers -> numbers.size() == count);
    }

    private static boolean isDistinctPerFile(List<List<Integer>> numbersByFile, int position) {
        final long distinct = numbersByFile.stream()
                .map(numbers -> numbers.get(position))
                .distinct()
                .count();

        return distinct == numbersByFile.size();
    }

    /**
     * Número de cada arquivo pela posição do nome na ordem natural — trechos numéricos
     * comparados como números ({@code "E2"} antes de {@code "E10"}) —, na ordem de
     * {@code fileNames}.
     */
    private static List<OptionalInt> numberByNameOrder(List<String> fileNames) {
        final List<String> sortedNames = fileNames.stream()
                .sorted(EpisodeNumberExtractor::compareNatural)
                .toList();

        return fileNames.stream()
                .map(fileName -> OptionalInt.of(sortedNames.indexOf(fileName) + 1))
                .toList();
    }

    private static int compareNatural(String left, String right) {
        final Matcher leftChunks = NATURAL_CHUNK.matcher(left);
        final Matcher rightChunks = NATURAL_CHUNK.matcher(right);

        while (leftChunks.find() && rightChunks.find()) {
            final String leftChunk = leftChunks.group();
            final String rightChunk = rightChunks.group();

            final int comparison = isDigits(leftChunk) && isDigits(rightChunk)
                    ? new BigInteger(leftChunk).compareTo(new BigInteger(rightChunk))
                    : leftChunk.compareToIgnoreCase(rightChunk);

            if (comparison != 0) {
                return comparison;
            }
        }

        return left.compareToIgnoreCase(right);
    }

    private static boolean isDigits(String chunk) {
        return Character.isDigit(chunk.charAt(0));
    }

    /** Todos os números isolados por separador do nome, na ordem em que aparecem. */
    private static List<Integer> separatedNumbers(String fileName) {
        final String nameWithoutExtension = EXTENSION.matcher(fileName).replaceFirst("");
        final Matcher matcher = SEPARATED_NUMBER.matcher(nameWithoutExtension);
        final List<Integer> numbers = new ArrayList<>();

        while (matcher.find()) {
            numbers.add(Integer.parseInt(matcher.group(1)));
        }

        return numbers;
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

    /**
     * Entre os números isolados por separador, prefere o último precedido por hífen — sinal de
     * que é o episódio, não um número que faz parte do título (ou do subtítulo, também
     * hifenado). Sem nenhum candidato assim, cai pro primeiro número isolado encontrado, igual
     * sempre foi.
     */
    private static OptionalInt lastHyphenPrecededOrFirstMatch(Pattern pattern, String text) {
        final Matcher matcher = pattern.matcher(text);
        Integer fallback = null;
        Integer lastHyphenPreceded = null;

        while (matcher.find()) {
            final int number = Integer.parseInt(matcher.group(1));

            if (fallback == null) {
                fallback = number;
            }

            if (precededByHyphen(text, matcher.start(1))) {
                lastHyphenPreceded = number;
            }
        }

        if (lastHyphenPreceded != null) {
            return OptionalInt.of(lastHyphenPreceded);
        }

        return fallback == null ? OptionalInt.empty() : OptionalInt.of(fallback);
    }

    private static boolean precededByHyphen(String text, int matchStart) {
        int i = matchStart - 1;

        while (i >= 0 && Character.isWhitespace(text.charAt(i))) {
            i--;
        }

        return i >= 0 && text.charAt(i) == '-';
    }

}
