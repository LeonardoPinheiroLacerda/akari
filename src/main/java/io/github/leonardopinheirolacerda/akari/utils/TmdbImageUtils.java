package io.github.leonardopinheirolacerda.akari.utils;

/**
 * Fábrica única das URLs completas do CDN de imagens do TMDB. O TMDB devolve só o
 * {@code filePath} relativo (ex.: {@code /abc.jpg}); estes métodos concatenam a base fixa
 * {@code image.tmdb.org/t/p/} com o identificador de tamanho de cada tipo de imagem.
 *
 * <p>Compartilhado pelos domínios {@code tmdb} e {@code anime} pra garantir tamanhos
 * idênticos — não replicar esta lógica. Todos os métodos são null-safe: {@code filePath}
 * {@code null}/em-branco devolve {@code null} sem lançar.
 */
public final class TmdbImageUtils {

    private static final String BASE = "https://image.tmdb.org/t/p/";

    private TmdbImageUtils() {
    }

    /** Poster pequeno ({@code w185}) — listas e grids densos. */
    public static String posterSmall(String filePath) {
        return build("w185", filePath);
    }

    /** Poster médio ({@code w342}) — grids padrão. */
    public static String posterMedium(String filePath) {
        return build("w342", filePath);
    }

    /** Poster grande ({@code w780}) — detalhe/hero (max não-original do TMDB). */
    public static String posterLarge(String filePath) {
        return build("w780", filePath);
    }

    /** Backdrop em {@code original} — banner/hero, mantém fidelidade em telas grandes. */
    public static String backdrop(String filePath) {
        return build("original", filePath);
    }

    /** Logo ({@code w500}) — cobre o uso típico sem sobre-dimensionar. */
    public static String logo(String filePath) {
        return build("w500", filePath);
    }

    /** Thumbnail de still de episódio ({@code w300}) — bate com o seletor de episódios. */
    public static String thumbnail(String filePath) {
        return build("w300", filePath);
    }

    private static String build(String size, String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return null;
        }
        final String normalized = filePath.startsWith("/") ? filePath : "/" + filePath;
        return BASE + size + normalized;
    }

}
