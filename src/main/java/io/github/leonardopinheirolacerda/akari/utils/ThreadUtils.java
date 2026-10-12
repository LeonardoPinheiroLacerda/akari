package io.github.leonardopinheirolacerda.akari.utils;

/**
 * Espera bloqueante que não propaga {@link InterruptedException} — usada como throttle
 * proativo entre chamadas (ex.: a ingestão espaçando requests pra AniList/TMDB).
 */
public final class ThreadUtils {

    private ThreadUtils() {
    }

    /**
     * Dorme o tempo informado; se interrompido, só restaura a interrupt flag da thread atual e
     * retorna — nunca lança. Quem chama decide se a interrupção deve abortar o fluxo, checando
     * {@code Thread.currentThread().isInterrupted()} depois.
     *
     * @param millis tempo de espera, em milissegundos
     */
    public static void sleepUninterruptibly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
