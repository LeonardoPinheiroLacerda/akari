package io.github.leonardopinheirolacerda.akari.domain.anime.pairing;

import io.github.leonardopinheirolacerda.akari.domain.anime.model.FuzzyDate;
import io.quarkus.logging.Log;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Datas usadas na resolução do pairing: a de exibição de um episódio no TMDB e as datas parciais
 * de estreia e fim de um anime na AniList ({@link FuzzyDate}).
 */
public final class AirDates {

    private AirDates() {
    }

    /**
     * Data de exibição de um episódio do TMDB.
     *
     * @param airDate data em ISO-8601, como o TMDB devolve
     * @return a data; vazio se não houver ou se estiver fora do formato
     */
    public static Optional<LocalDate> parse(String airDate) {
        if (airDate == null || airDate.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(LocalDate.parse(airDate));
        } catch (DateTimeParseException e) {
            Log.warnf("Data de exibição do TMDB fora do formato ISO: %s", airDate);
            return Optional.empty();
        }
    }

    /**
     * Primeiro dia do período que a data parcial cobre (só o ano: 1º de janeiro).
     *
     * @param date data parcial, com ao menos o ano
     * @return o primeiro dia
     */
    public static LocalDate firstDayOf(FuzzyDate date) {
        if (date.month == null) {
            return LocalDate.of(date.year, 1, 1);
        }

        if (date.day == null) {
            return LocalDate.of(date.year, date.month, 1);
        }

        return LocalDate.of(date.year, date.month, date.day);
    }

    /**
     * Último dia do período que a data parcial cobre (só o ano: 31 de dezembro).
     *
     * @param date data parcial, com ao menos o ano
     * @return o último dia
     */
    public static LocalDate lastDayOf(FuzzyDate date) {
        if (date.month == null) {
            return LocalDate.of(date.year, 12, 31);
        }

        if (date.day == null) {
            return YearMonth.of(date.year, date.month)
                    .atEndOfMonth();
        }

        return LocalDate.of(date.year, date.month, date.day);
    }

    /**
     * Confere se a data parcial tem dia, mês e ano — um período exato.
     *
     * @param date data parcial; pode ser {@code null}
     * @return {@code true} se ano, mês e dia estão preenchidos
     */
    public static boolean isComplete(FuzzyDate date) {
        return date != null && date.year != null && date.month != null && date.day != null;
    }

}
