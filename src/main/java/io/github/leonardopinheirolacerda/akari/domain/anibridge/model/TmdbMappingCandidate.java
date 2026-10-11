package io.github.leonardopinheirolacerda.akari.domain.anibridge.model;

import io.github.leonardopinheirolacerda.akari.api.dto.TmdbMediaType;
import java.util.List;

/**
 * Um candidato de binding TMDB pra um anime, com todos os segments do anibridge que apontam pra
 * esse {@code tmdbId}/{@code mediaType}. Quando um anime bate em mais de um candidato (filme e
 * série simultaneamente, por exemplo), o {@code AnibridgeResolutionService} escolhe o que não é
 * suspeito (sem sobreposição interna, sem cobrir mais episódios do que o anime tem de verdade) —
 * cobertura de episódios só desempata entre candidatos do mesmo nível.
 *
 * @param tmdbId id do recurso no TMDB
 * @param mediaType tipo do recurso
 * @param segments segments do anibridge que compõem esse candidato
 */
public record TmdbMappingCandidate(Integer tmdbId, TmdbMediaType mediaType, List<MappingSegment> segments) {
}
