package io.github.leonardopinheirolacerda.akari.utils;

import jakarta.ws.rs.WebApplicationException;

/**
 * Classificação de falhas de REST client cru, compartilhada entre integrações (AniList, TMDB).
 */
public final class HttpErrors {

    private HttpErrors() {
    }

    /**
     * @param e falha capturada de uma chamada a REST client
     * @return {@code true} se o upstream respondeu {@code 404} — recurso não encontrado, não
     *         falha de infraestrutura
     */
    public static boolean isNotFound(Exception e) {
        return e instanceof WebApplicationException wae && wae.getResponse().getStatus() == 404;
    }

}
