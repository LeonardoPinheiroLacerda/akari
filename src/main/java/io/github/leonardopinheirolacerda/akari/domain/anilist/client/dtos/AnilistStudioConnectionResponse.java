package io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos;

import java.util.List;

/**
 * Bloco {@code studios} de um {@code Media} — a query já filtra com {@code isMain: true}, então
 * só vêm os estúdios principais.
 *
 * @param nodes estúdios principais do anime consultado
 */
public record AnilistStudioConnectionResponse(List<AnilistStudioResponse> nodes) {
}
