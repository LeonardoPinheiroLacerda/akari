package io.github.leonardopinheirolacerda.akari.services;

import io.github.leonardopinheirolacerda.akari.api.dto.SettingResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.UpdateSettingRequest;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.github.leonardopinheirolacerda.akari.mapper.SettingMapper;
import io.github.leonardopinheirolacerda.akari.model.Setting;
import io.quarkus.hibernate.orm.panache.Panache;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class SettingService {

    @Inject
    SettingMapper mapper;

    public List<SettingResponse> listSettings(String name) {
        final List<Setting> settings = name == null
                ? Setting.listAll()
                : Setting.findByName(name);

        final List<SettingResponse> responses = mapper.toResponseList(settings);

        responses.forEach(SettingService::maskSecretValue);

        Log.infof("Listadas %d preferências", responses.size());

        return responses;
    }

    @Transactional
    public SettingResponse updateSetting(String key, UpdateSettingRequest updateSettingRequest) {
        Log.infof("Atualizando a preferência %s", key);

        final Setting setting = findOrThrow(key);

        setting.value = updateSettingRequest.getValue();

        // Força o flush aqui: @UpdateTimestamp só atualiza o updatedAt em memória durante o
        // flush, que sem isso só aconteceria no commit da transação — depois da resposta já
        // ter sido montada com o valor antigo.
        Panache.flush();

        Log.infof("Preferência %s atualizada com sucesso", key);

        final SettingResponse response = mapper.toResponse(setting);
        maskSecretValue(response);
        return response;
    }

    public Setting findOrThrow(String key) {
        return Setting.find(key)
                .orElseThrow(() -> {
                    Log.warnf("Preferência %s não encontrada", key);
                    return new ResourceNotFoundException("Não foi possível localizar uma preferência com a chave informada");
                });
    }

    private static void maskSecretValue(SettingResponse response) {
        if (Boolean.TRUE.equals(response.getSecret())) {
            response.setValue("***");
        }
    }

}