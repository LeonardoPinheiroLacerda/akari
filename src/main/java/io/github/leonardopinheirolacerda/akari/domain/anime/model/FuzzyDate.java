package io.github.leonardopinheirolacerda.akari.domain.anime.model;

import jakarta.persistence.Embeddable;

/**
 * Data parcial da AniList, como ela devolve — qualquer componente pode faltar (ex.: só o ano de
 * um anime anunciado).
 */
@Embeddable
public class FuzzyDate {

    public Integer year;

    public Integer month;

    public Integer day;

}
