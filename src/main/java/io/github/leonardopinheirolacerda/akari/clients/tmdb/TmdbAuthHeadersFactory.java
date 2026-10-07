package io.github.leonardopinheirolacerda.akari.clients.tmdb;

import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationNotConfiguredException;
import io.github.leonardopinheirolacerda.akari.services.SettingService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;

/**
 * Injeta o header {@code Authorization: Bearer <token>} em toda chamada do {@link TmdbClient},
 * com o token vindo da preferência {@code tmdb.api-key}.
 *
 * <p>Precisa ser um {@link ClientHeadersFactory} (bean CDI) porque o valor é dinâmico — vem da
 * base de {@code settings}, não de config estática.
 */
@ApplicationScoped
public class TmdbAuthHeadersFactory implements ClientHeadersFactory {

    @Inject
    SettingService settingService;

    @Override
    public MultivaluedMap<String, String> update(
            MultivaluedMap<String, String> incomingHeaders,
            MultivaluedMap<String, String> clientOutgoingHeaders) {
        final String apiKey = settingService.getTmdbApiKey();

        if (apiKey == null || apiKey.isBlank()) {
            throw new IntegrationNotConfiguredException(
                    "Integração com o TMDB não configurada. Cadastre o token da API na preferência 'tmdb.api-key' pra habilitar a busca.");
        }

        final MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        headers.add("Authorization", "Bearer " + apiKey);

        return headers;
    }

}