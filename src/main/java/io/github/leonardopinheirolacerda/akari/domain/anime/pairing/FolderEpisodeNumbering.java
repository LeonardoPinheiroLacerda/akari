package io.github.leonardopinheirolacerda.akari.domain.anime.pairing;

import io.github.leonardopinheirolacerda.akari.domain.ingestion.extractor.EpisodeNumberExtractor;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Primeira etapa da resolução do pairing: o número de episódio de cada arquivo da pasta.
 * Complementa o {@link EpisodeNumberExtractor}, que olha um arquivo por vez, olhando a pasta
 * inteira — só quando o arquivo por arquivo devolve o mesmo número em mais de um arquivo, sinal
 * de que o extrator pegou um número fixo do nome (temporada, número do título) em vez do
 * episódio.
 */
public final class FolderEpisodeNumbering {

    private static final Pattern NATURAL_CHUNK = Pattern.compile("\\d+|\\D+");

    private FolderEpisodeNumbering() {
    }

    /**
     * Número de episódio de cada arquivo de uma pasta, olhando a pasta inteira.
     *
     * <p>Usa o {@link EpisodeNumberExtractor#extract} de cada arquivo, a não ser que ele não
     * ache número em nenhum arquivo da pasta, ou devolva o mesmo número em mais de um arquivo
     * (ex.: {@code "Season 1 Episode 01"} e {@code "Season 1 Episode 02"} saem ambos 1, a
     * temporada). Pasta sem nenhum número vai direto pra ordem dos nomes (item 2, com a trava
     * do total de episódios). Com número repetido, tenta, nesta ordem:
     * <ol>
     *   <li>o número que varia entre os arquivos com número repetido: todos esses nomes têm a
     *       mesma quantidade de números isolados por separador, e exatamente uma posição tem um
     *       valor diferente em cada um — ela é o episódio. Os arquivos com número único mantêm
     *       o do {@link EpisodeNumberExtractor#extract} (ex.: pasta que mistura
     *       {@code "OVA - 08"} de um lançamento
     *       com {@code "2_Ova 01"} de outro), desde que nenhum número se repita no fim;</li>
     *   <li>a ordem dos nomes (o primeiro é o episódio 1), comparando trechos numéricos como
     *       números, só quando a pasta tem exatamente {@code expectedEpisodes} arquivos e todos
     *       os nomes têm a mesma quantidade de números isolados — sem essas travas, um arquivo
     *       extra (OVA, abertura, {@code v2}) ou um lançamento de outro grupo deslocaria os
     *       seguintes sem aviso.</li>
     * </ol>
     * Se nenhuma das duas se aplica, devolve o resultado arquivo por arquivo. Nenhuma das duas
     * depende da ordem em que os nomes chegam: a ordem natural dos arquivos de vídeo também sai
     * do {@link EpisodeNumberExtractor#extract}, e é justamente nessas pastas que ela fica
     * embaralhada.
     *
     * @param fileNames nomes dos arquivos da pasta, com extensão
     * @param expectedEpisodes total de episódios do anime; {@code null} se desconhecido
     * @return um número por arquivo, na mesma ordem de {@code fileNames}
     */
    public static List<OptionalInt> number(List<String> fileNames, Integer expectedEpisodes) {
        final List<OptionalInt> perFile = fileNames.stream()
                .map(EpisodeNumberExtractor::extract)
                .toList();

        final boolean hasNoNumber = perFile.stream()
                .noneMatch(OptionalInt::isPresent);

        if (hasNoNumber) {
            return isFullSeason(fileNames, expectedEpisodes)
                    ? numberByNameOrder(fileNames)
                    : perFile;
        }

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
                .map(EpisodeNumberExtractor::extractSeparatedNumbers)
                .toList();

        if (isFullSeason(fileNames, expectedEpisodes) && haveSameNumberCount(numbersByFile)) {
            return numberByNameOrder(fileNames);
        }

        return perFile;
    }

    /** A pasta tem exatamente o total de episódios do anime. */
    private static boolean isFullSeason(List<String> fileNames, Integer expectedEpisodes) {
        return expectedEpisodes != null && fileNames.size() == expectedEpisodes;
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
    private static Optional<List<OptionalInt>> replaceRepeatedByVaryingNumbers(
            List<String> fileNames,
            List<OptionalInt> perFile,
            List<Integer> repeatedIndexes) {

        final List<List<Integer>> numbersByFile = repeatedIndexes.stream()
                .map(index -> EpisodeNumberExtractor.extractSeparatedNumbers(fileNames.get(index)))
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
            final Integer episodeNumber = numbersByFile.get(i)
                    .get(position);

            replaced.set(repeatedIndexes.get(i), OptionalInt.of(episodeNumber));
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
                .sorted(FolderEpisodeNumbering::compareNatural)
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
}
