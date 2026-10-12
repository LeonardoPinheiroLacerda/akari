package io.github.leonardopinheirolacerda.akari.domain.setting.resource;

import io.github.leonardopinheirolacerda.akari.api.SettingApi;
import io.github.leonardopinheirolacerda.akari.api.dto.SettingResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.UpdateSettingRequest;
import io.github.leonardopinheirolacerda.akari.domain.setting.service.SettingService;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Implementa {@link SettingApi} — preferências administráveis em {@code /v1/settings}.
 *
 * <p>Só traduz HTTP ↔ service, sem lógica própria: toda regra fica no {@link SettingService}.
 */
@ApplicationScoped
public class SettingResource implements SettingApi {

    @Inject
    SettingService settingService;

    /**
     * {@code GET /v1/settings} — lista as preferências, opcionalmente filtrando por nome.
     *
     * @param name substring case-insensitive da chave a filtrar; {@code null} lista todas
     * @return preferências ordenadas pela chave, com {@code 200 OK}
     */
    @Override
    public List<SettingResponse> listSettings(String name) {
        return settingService.listSettings(name);
    }

    /**
     * {@code PUT /v1/settings/{key}} — atualiza o valor de uma preferência existente.
     *
     * @param key chave da preferência a atualizar
     * @param updateSettingRequest novo valor
     * @return a preferência atualizada, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se a chave não existir
     */
    @Override
    public SettingResponse updateSetting(String key, UpdateSettingRequest updateSettingRequest) {
        return settingService.updateSetting(key, updateSettingRequest);
    }
}
