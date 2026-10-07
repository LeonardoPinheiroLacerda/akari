package io.github.leonardopinheirolacerda.akari.clients.anilist.dtos;

/**
 * Uma relação entre o anime consultado e {@link #node()}.
 *
 * @param relationType tipo bruto da relação na AniList (ex.: {@code SEQUEL}, {@code PREQUEL})
 * @param node anime do outro lado da relação
 */
public record AnilistRelationEdgeResponse(String relationType, AnilistRelationNodeResponse node) {
}
