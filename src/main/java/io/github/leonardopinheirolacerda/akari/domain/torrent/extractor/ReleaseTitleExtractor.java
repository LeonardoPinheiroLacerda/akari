package io.github.leonardopinheirolacerda.akari.domain.torrent.extractor;

import io.github.leonardopinheirolacerda.akari.api.dto.TorrentTrackType;
import io.github.leonardopinheirolacerda.akari.domain.torrent.model.EditionTraits;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Extrai do título de uma release de torrent o grupo de fansub, a resolução, a versão, a edição
 * (chave de agrupamento e rótulo legível), as características técnicas da edição e o tipo de
 * legenda. O número do episódio fica com o
 * {@code EpisodeNumberExtractor} da ingestão, o mesmo usado nos arquivos já baixados.
 *
 * <p>A edição é construída <strong>só</strong> a partir do grupo e de descritores técnicos
 * (temporada, resolução, fonte, mídia, áudio, codec, profundidade de cor): nome e número do
 * episódio e qualquer outro texto livre são ignorados, então todos os episódios da mesma release
 * caem na mesma edição.
 */
public final class ReleaseTitleExtractor {

    private static final Pattern LEADING_GROUP = Pattern.compile("^\\[([^\\]]+)\\]");

    private static final Pattern RESOLUTION = Pattern.compile("(?i)\\b(?:2160p|1080p|720p|480p|4k)\\b");

    // "07v2" e "[v2]": o "v" vem colado num dígito ou depois de uma fronteira de palavra.
    private static final Pattern VERSION = Pattern.compile("(?i)(?:\\d|\\b)v(\\d{1,2})\\b");

    private static final Integer DEFAULT_VERSION = 1;

    private static final Pattern SEASON = Pattern.compile("(?i)\\bS\\d{1,2}(?=E\\d|[^A-Za-z0-9]|$)");

    private static final Pattern SOURCE = Pattern.compile("\\b(?:AMZN|NF|CR|BILI|HULU|DSNP|ATVP|LIV|MAX|ABEMA|VRV|FUNI|ADN)\\b");

    private static final Pattern MEDIUM = Pattern.compile("(?i)\\b(?:WEB-?DL|WEB-?Rip|Blu-?Ray|BD-?Rip|HDTV|DVD-?Rip)\\b");

    // MULTi/DUAL contam como áudio só quando não são "Multi-Subs" (legenda).
    private static final Pattern AUDIO_TYPE = Pattern.compile("(?i)\\b(?:MULTi|DUAL)\\b(?!-?Subs?)");

    private static final Pattern AUDIO_CODEC = Pattern.compile(
            "(?i)\\b(?:DDP?\\s?\\d\\.\\d|E?-?AC-?3|AAC(?:\\s?\\d\\.\\d)?|FLAC|Opus|TrueHD|DTS(?:-?HD)?)\\b");

    private static final Pattern VIDEO_CODEC = Pattern.compile("(?i)\\b(?:x[\\s.]?26[45]|H[\\s.]?26[45]|HEVC|AVC|AV1)\\b");

    private static final Pattern BIT_DEPTH = Pattern.compile("(?i)\\b(?:10-?bit|8-?bit|HDR10?|Dolby\\s?Vision|DV)\\b");

    // "Multi-Subs", "MultiSub", "Dual-Subs": legendas em mais de um idioma (não entra na edição).
    private static final Pattern SUBTITLE_TYPE = Pattern.compile("(?i)\\b(multi|dual)-?subs?\\b");

    // Descritores técnicos, na ordem natural do rótulo.
    private static final List<Pattern> EDITION_TOKENS = List.of(
            SEASON,
            RESOLUTION,
            SOURCE,
            MEDIUM,
            AUDIO_TYPE,
            AUDIO_CODEC,
            VIDEO_CODEC,
            BIT_DEPTH
    );

    // "H 264", "H.264" e "H264" viram o mesmo token na chave.
    private static final Pattern TOKEN_SEPARATORS = Pattern.compile("[\\s.\\-]");

    private static final String NO_TOKENS_LABEL = "(sem specs)";

    private ReleaseTitleExtractor() {
    }

    /**
     * Extrai o grupo de fansub dos colchetes no início do título.
     *
     * @param title título da release
     * @return o grupo (ex.: {@code "SubsPlease"} de {@code "[SubsPlease] ..."}); vazio se o título
     *         não começa com colchetes
     */
    public static Optional<String> extractGroup(String title) {
        final Matcher matcher = LEADING_GROUP.matcher(title);

        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1).trim());
    }

    /**
     * Extrai a resolução do título.
     *
     * @param title título da release
     * @return a resolução em minúsculas (ex.: {@code "1080p"}); vazio se o título não traz nenhuma
     */
    public static Optional<String> extractResolution(String title) {
        return find(RESOLUTION, title)
                .map(resolution -> resolution.toLowerCase(Locale.ROOT));
    }

    /**
     * Extrai a versão da release ({@code v2}, {@code v3}...), usada para preferir a versão
     * corrigida de um mesmo episódio.
     *
     * @param title título da release
     * @return o número da versão; {@code 1} quando o título não traz nenhuma
     */
    public static Integer extractVersion(String title) {
        final Matcher matcher = VERSION.matcher(title);

        if (!matcher.find()) {
            return DEFAULT_VERSION;
        }
        return Integer.valueOf(matcher.group(1));
    }

    /**
     * Monta a chave de edição: o grupo mais os descritores técnicos normalizados e ordenados.
     *
     * @param title título da release
     * @param group grupo de fansub; {@code null} sem grupo
     * @return a chave, igual para todos os episódios da mesma edição (ex.:
     *         {@code "subsplease|1080P"})
     */
    public static String extractEditionKey(String title, String group) {
        final String normalizedGroup = Objects
                .requireNonNullElse(group, "")
                .toLowerCase(Locale.ROOT);

        final String tokens = editionTokens(title)
                .stream()
                .map(token -> TOKEN_SEPARATORS.matcher(token).replaceAll(""))
                .map(token -> token.toUpperCase(Locale.ROOT))
                .sorted()
                .collect(Collectors.joining("|"));

        return normalizedGroup + "|" + tokens;
    }

    /**
     * Monta o rótulo legível da edição: os descritores técnicos na ordem natural.
     *
     * @param title título da release
     * @return o rótulo (ex.: {@code "S01 1080p CR WEB-DL MULTi AAC2.0 H.264"}), ou
     *         {@code "(sem specs)"} quando o título não traz nenhum descritor
     */
    public static String extractEditionLabel(String title) {
        final List<String> tokens = editionTokens(title);

        if (tokens.isEmpty()) {
            return NO_TOKENS_LABEL;
        }
        return String.join(" ", tokens);
    }

    /**
     * Extrai as características técnicas da edição: os mesmos descritores que formam a chave, cada
     * um no seu campo.
     *
     * @param title título da release
     * @return as características; cada uma {@code null} quando o título não traz o descritor
     */
    public static EditionTraits extractTraits(String title) {
        return new EditionTraits(
                extractResolution(title)
                        .orElse(null),
                find(SOURCE, title)
                        .orElse(null),
                find(MEDIUM, title)
                        .orElse(null),
                find(AUDIO_TYPE, title)
                        .map(ReleaseTitleExtractor::trackTypeOf)
                        .orElse(null),
                find(AUDIO_CODEC, title)
                        .orElse(null),
                find(VIDEO_CODEC, title)
                        .map(ReleaseTitleExtractor::videoCodecOf)
                        .orElse(null),
                find(BIT_DEPTH, title)
                        .orElse(null)
        );
    }

    /**
     * Extrai o tipo de legenda: se o título indica legendas em mais de um idioma.
     *
     * @param title título da release
     * @return {@code MULTI} ({@code "Multi-Subs"}) ou {@code DUAL} ({@code "Dual-Subs"}); vazio se o
     *         título não indica
     */
    public static Optional<TorrentTrackType> extractSubtitleType(String title) {
        return find(SUBTITLE_TYPE, title)
                .map(ReleaseTitleExtractor::trackTypeOf);
    }

    private static Optional<String> find(Pattern pattern, String title) {
        final Matcher matcher = pattern.matcher(title);

        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group());
    }

    private static TorrentTrackType trackTypeOf(String token) {
        if (token.toUpperCase(Locale.ROOT).startsWith("DUAL")) {
            return TorrentTrackType.DUAL;
        }
        return TorrentTrackType.MULTI;
    }

    // "x265", "H 265", "HEVC" → "H.265"; "x264", "H.264", "AVC" → "H.264".
    private static String videoCodecOf(String token) {
        final String normalized = TOKEN_SEPARATORS
                .matcher(token)
                .replaceAll("")
                .toUpperCase(Locale.ROOT);

        if (normalized.contains("265") || normalized.equals("HEVC")) {
            return "H.265";
        }
        if (normalized.contains("264") || normalized.equals("AVC")) {
            return "H.264";
        }
        return "AV1";
    }

    private static List<String> editionTokens(String title) {
        return EDITION_TOKENS
                .stream()
                .map(pattern -> pattern.matcher(title))
                .filter(Matcher::find)
                .map(Matcher::group)
                .toList();
    }
}
