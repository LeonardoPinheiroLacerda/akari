package io.github.leonardopinheirolacerda.akari.clients.anilist;

import io.github.leonardopinheirolacerda.akari.clients.anilist.dtos.AnilistGraphqlRequest;
import io.github.leonardopinheirolacerda.akari.clients.anilist.dtos.AnilistMediaQueryResponse;
import io.github.leonardopinheirolacerda.akari.clients.anilist.dtos.AnilistSearchResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * REST Client (MicroProfile) para o endpoint GraphQL único da AniList.
 *
 * <p>A URL base é resolvida pela chave {@code quarkus.rest-client.anilist.url} — já aponta pro
 * endpoint completo (não tem path próprio), então o método só faz POST na raiz.
 *
 * <p>Esta interface é o transporte cru — não faz retry, guard, nem tradução de exceções. Fault
 * tolerance e embrulho em {@code IntegrationException} ficam no service que chamar.
 */
@RegisterRestClient(configKey = "anilist")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface AnilistClient {

    /**
     * Executa uma query GraphQL contra a AniList.
     *
     * @param request corpo com a query e as variáveis
     * @return envelope da resposta, com {@code data.Page} preenchido em caso de sucesso
     */
    @POST
    AnilistSearchResponse search(AnilistGraphqlRequest request);

    /**
     * Executa uma query GraphQL contra a AniList.
     *
     * @param request corpo com a query e as variáveis
     * @return envelope da resposta, com {@code data.Media} preenchido em caso de sucesso
     */
    @POST
    AnilistMediaQueryResponse findById(AnilistGraphqlRequest request);

}
