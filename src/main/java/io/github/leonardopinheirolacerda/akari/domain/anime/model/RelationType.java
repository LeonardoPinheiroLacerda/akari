package io.github.leonardopinheirolacerda.akari.domain.anime.model;

import java.util.List;
import java.util.stream.Stream;

/**
 * Tipo de relação direcionada entre dois animes. Alinhado com o enum da AniList; tipos sem
 * relevância pro grafo ({@code ADAPTATION}, {@code CHARACTER}, {@code CONTAINS},
 * {@code COMPILATION}, {@code SOURCE}) caem em {@link #OTHER} na conversão, e valores
 * desconhecidos caem em {@link #UNKNOWN}.
 */
public enum RelationType {

    SEQUEL,
    PREQUEL,
    SIDE_STORY,
    PARENT,
    SUMMARY,
    ALTERNATIVE,
    SPIN_OFF,
    OTHER,
    UNKNOWN;

    /**
     * Tipos que, chegando num anime, indicam que ele é derivado de quem aponta — terceira
     * prioridade do pai automático, depois de {@code PREQUEL} e {@code PARENT} saindo dele.
     * {@code ALTERNATIVE}, {@code OTHER} e {@code CHARACTER} ficam de fora: nunca geram pai.
     */
    public static final List<RelationType> DERIVED_LINKS = List.of(SIDE_STORY, SPIN_OFF, SUMMARY);

    /**
     * Todos os tipos que podem gerar pai automático, em qualquer direção — o que a resolução
     * de franquias precisa ler de {@code anime_relation}.
     */
    public static final List<RelationType> AUTO_PARENT_LINKS = Stream
            .concat(Stream.of(PREQUEL, PARENT), DERIVED_LINKS.stream())
            .toList();

    /**
     * Formatos brutos da AniList ({@code Anime#format}) que classificam um membro da cadeia de
     * {@code SEQUEL} como temporada, pra {@code GET /v1/metadata/animes/{anilistId}/franchise}.
     */
    public static final List<String> SERIES_FORMATS = List.of("TV", "TV_SHORT", "ONA");

    /**
     * Formato bruto da AniList que classifica um membro fora de {@code seasons} como filme, pra
     * {@code GET /v1/metadata/animes/{anilistId}/franchise}; os demais formatos são especiais.
     */
    public static final List<String> MOVIE_FORMATS = List.of("MOVIE");

}
