package io.github.leonardopinheirolacerda.akari.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ordem em que um texto ou imagem do TMDB é procurado entre os idiomas das preferências.
 *
 * <p>Compartilhado pelos overrides da série (domínio {@code anime}) e pelos episódios (domínio
 * {@code tmdb}) pra que os dois procurem na mesma ordem — não replicar esta lógica.
 */
public final class TmdbLanguageUtils {

    private TmdbLanguageUtils() {
    }

    /**
     * Ordem de procura dos idiomas, do mais específico ao mais amplo: pra cada idioma, na ordem
     * recebida, idioma e país ({@code pt-BR}) quando ele tem país, e depois só o idioma
     * ({@code pt}). Idiomas {@code null} são ignorados.
     *
     * @param languages idiomas em ordem de preferência
     * @return os idiomas a procurar, sem repetição
     */
    public static List<Locale> languageChain(Locale... languages) {
        final List<Locale> chain = new ArrayList<>();

        for (Locale locale : languages) {
            if (locale == null) {
                continue;
            }

            chain.add(locale);

            if (!locale.getCountry().isEmpty()) {
                chain.add(Locale.of(locale.getLanguage()));
            }
        }

        return chain.stream()
                .distinct()
                .toList();
    }

}
