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
     * quebram a raiz natural da franquia. Fonte única da regra, usada na paginação de raízes,
     * na classificação de nó no grafo, e na subida até a raiz de {@code GET
     * /v1/metadata/animes/{anilistId}/franchise} ({@code RelationGraph#findFranchiseRootId}) —
     * essa última soma ainda {@code SUMMARY} com a direção invertida
     * ({@code A -[SUMMARY]-> B} quer dizer "B é resumo de A", ou seja, A é o primário; o
     * contrário do sentido de {@code PREQUEL}/{@code PARENT}, onde quem tem a aresta saindo é o
     * descendente). {@code ALTERNATIVE} fica de fora da subida de propósito: a AniList tagueia
     * esse tipo de forma simétrica com frequência (os dois lados se apontam como
     * {@code ALTERNATIVE} um do outro), o que criaria um ciclo de 2 nós sem um lado
     * objetivamente "mais ancestral" — só conta pra fechamento ({@link #FRANCHISE_LINKS}), não
     * pra decidir a raiz.
     */
    public static final List<RelationType> ANCESTOR_LINKS = List.of(PREQUEL, PARENT);

    /**
     * Tipos que compõem a franquia pra {@code GET /v1/metadata/animes/{anilistId}/franchise}
     * (temporadas via {@code SEQUEL}, especiais via {@code SIDE_STORY}/{@code SPIN_OFF}/
     * {@code SUMMARY}/{@code PARENT}/{@code ALTERNATIVE}) — {@code PREQUEL} e {@code OTHER}
     * ficam de fora de propósito. {@code PARENT}/{@code ALTERNATIVE}/{@code SUMMARY} entram
     * apesar de também contarem (com sentido ou direção diferentes — ver {@link
     * #ANCESTOR_LINKS}) pra subida até a raiz: a AniList usa esses tipos tanto pra "isso
     * descende de/é versão de um título anterior" quanto pra "isso é conteúdo extra/alternativo
     * de um título desta franquia" (sentido de especial) — como o fechamento é bidirecional, as
     * duas pontas acabam cobertas.
     */
    public static final List<RelationType> FRANCHISE_LINKS =
            List.of(SEQUEL, SIDE_STORY, SPIN_OFF, SUMMARY, PARENT, ALTERNATIVE);

    /**
     * Subconjunto de {@link #FRANCHISE_LINKS} sem {@code SEQUEL} — ligar um anime à franquia
     * por qualquer um desses tipos basta pra classificá-lo como especial, mesmo que ele também
     * esteja na cadeia de {@code SEQUEL} com um formato fora de {@link #SERIES_FORMATS}/
     * {@link #SPECIAL_FORMATS} (que isolado o excluiria da franquia).
     */
    public static final List<RelationType> SPECIAL_LINKS = List.of(SIDE_STORY, SPIN_OFF, SUMMARY, PARENT, ALTERNATIVE);

    /**
     * Formatos brutos da AniList ({@code Anime#format}) que classificam um anime da cadeia de
     * {@code SEQUEL} como temporada, pra {@code GET /v1/metadata/animes/{anilistId}/franchise}.
     */
    public static final List<String> SERIES_FORMATS = List.of("TV", "TV_SHORT", "ONA");

    /**
     * Formato bruto da AniList que classifica um anime da cadeia de {@code SEQUEL} (ou ligado
     * por {@link #SPECIAL_LINKS}) como filme, pra {@code GET
     * /v1/metadata/animes/{anilistId}/franchise}. Disjunto de {@link #SPECIAL_FORMATS} de
     * propósito — nenhum formato conta como as duas coisas ao mesmo tempo.
     */
    public static final List<String> MOVIE_FORMATS = List.of("MOVIE");

    /**
     * Formatos brutos da AniList que classificam um anime da cadeia de {@code SEQUEL} como
     * especial (não-filme), pra {@code GET /v1/metadata/animes/{anilistId}/franchise}. Formatos
     * fora de {@link #SERIES_FORMATS}, {@link #MOVIE_FORMATS} e desta lista ficam fora da
     * franquia, a menos que tenham também uma aresta própria de {@link #SPECIAL_LINKS}.
     */
    public static final List<String> SPECIAL_FORMATS = List.of("OVA", "SPECIAL");

}
