package io.github.leonardopinheirolacerda.akari.clients.anilist.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Bloco de títulos de um {@code Media} da AniList.
 *
 * @param romaji romanização; convenção da AniList quando não há preferência
 * @param english título em inglês, ou {@code null}
 * @param nativeTitle título em japonês (kanji/kana) — {@code native} na AniList, renomeado
 *                    porque colide com palavra reservada em Java
 * @param userPreferred título preferido do usuário logado; sem token, coincide com {@code romaji}
 */
public record AnilistTitleResponse(
        String romaji,
        String english,
        @JsonProperty("native") String nativeTitle,
        String userPreferred
) {
}
