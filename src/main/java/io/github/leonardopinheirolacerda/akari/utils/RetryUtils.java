package io.github.leonardopinheirolacerda.akari.utils;

import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.quarkus.logging.Log;
import jakarta.ws.rs.WebApplicationException;
import java.util.function.Supplier;

/**
 * Retry com backoff pra chamada a sistema externo que leva rate limit (429). Agnóstico de qual
 * integração chama — qualquer service que embrulhe a falha do client em {@link
 * IntegrationException} pode usar.
 *
 * <p>Só retenta em 429 — qualquer outro erro (404, 502, timeout) propaga na primeira tentativa.
 * Em 429, espera indefinidamente com backoff exponencial até a API responder OK — não desiste
 * depois de N tentativas.
 */
public final class RetryUtils {

    private static final long INITIAL_DELAY_MS = 2_000;
    private static final long MAX_DELAY_MS = 60_000;

    private RetryUtils() {
    }

    /**
     * Executa a chamada, retentando com backoff enquanto o erro for um 429.
     *
     * @param call chamada a executar (tipicamente um método de service que embrulha o client em
     *             {@link IntegrationException})
     * @return o resultado da chamada
     */
    public static <T> T withRateLimitRetry(Supplier<T> call) {
        long delayMs = INITIAL_DELAY_MS;

        while (true) {
            try {
                return call.get();
            } catch (IntegrationException e) {
                if (!isRateLimited(e)) {
                    throw e;
                }

                Log.warnf("Rate limit atingido, aguardando %dms antes de tentar de novo", delayMs);
                sleep(delayMs);
                delayMs = Math.min(delayMs * 2, MAX_DELAY_MS);
            }
        }
    }

    private static boolean isRateLimited(IntegrationException e) {
        return e.getCause() instanceof WebApplicationException webApplicationException
                && webApplicationException.getResponse().getStatus() == 429;
    }

    private static void sleep(long delayMs) {
        ThreadUtils.sleepUninterruptibly(delayMs);

        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("Interrompido durante espera de rate limit");
        }
    }

}
