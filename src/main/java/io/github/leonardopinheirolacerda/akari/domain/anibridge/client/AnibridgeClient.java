package io.github.leonardopinheirolacerda.akari.domain.anibridge.client;

import io.github.leonardopinheirolacerda.akari.domain.anibridge.client.dtos.GithubReleaseResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * REST Client (MicroProfile) para a API do GitHub, usado só pra descobrir a release mais
 * recente do dataset do anibridge.
 *
 * <p>URL base resolvida por {@code quarkus.rest-client.anibridge.url}. O download do asset em
 * si ({@code mappings.min.json}) não passa por aqui — é feito com {@code HttpClient} puro, já
 * que a URL de download do GitHub redireciona pra uma CDN em outro host.
 *
 * <p>Esta interface é o transporte cru — não faz retry nem tradução de exceções. Fault
 * tolerance e embrulho em {@code IntegrationException} ficam no service que chamar.
 */
@RegisterRestClient(configKey = "anibridge")
@Produces(MediaType.APPLICATION_JSON)
public interface AnibridgeClient {

    /**
     * {@code GET /repos/anibridge/anibridge-mappings/releases/latest}.
     *
     * @return a release mais recente, com a lista de assets pra localizar o
     *         {@code mappings.min.json}
     */
    @GET
    @Path("/repos/anibridge/anibridge-mappings/releases/latest")
    GithubReleaseResponse getLatestRelease();

}