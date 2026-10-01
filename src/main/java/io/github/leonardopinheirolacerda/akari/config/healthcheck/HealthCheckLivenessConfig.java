package io.github.leonardopinheirolacerda.akari.config.healthcheck;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

/**
 * Liveness check da aplicação, exposto pelo SmallRye Health em {@code /q/health/live}.
 *
 * <p>Não consulta dependências (banco, rclone, APIs externas): só sinaliza que o processo está de
 * pé e respondendo. Um orquestrador usa esse sinal para decidir se reinicia o container.
 */
@Liveness
@ApplicationScoped
public class HealthCheckLivenessConfig implements HealthCheck {

    /**
     * Responde sempre {@code UP} com o nome {@code "Liveness Health Check"} — se este método
     * executa, o processo está vivo.
     *
     * @return resposta de health {@code UP}, sem dados adicionais
     */
    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse
                .named("Liveness Health Check")
                .up()
                .build();
    }

}
