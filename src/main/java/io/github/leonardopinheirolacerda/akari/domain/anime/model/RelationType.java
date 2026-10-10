package io.github.leonardopinheirolacerda.akari.domain.anime.model;

import java.util.List;

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
     * Tipos que, saindo de um anime, indicam que ele descende de outro título — por isso
     * quebram a raiz natural da franquia. Fonte única da regra, usada tanto na paginação de
     * raízes quanto na classificação de nó no grafo.
     */
    public static final List<RelationType> ANCESTOR_LINKS = List.of(PREQUEL, PARENT);

}
