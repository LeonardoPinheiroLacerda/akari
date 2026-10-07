package io.github.leonardopinheirolacerda.akari.domain.setting.service;

import io.github.leonardopinheirolacerda.akari.api.dto.SettingResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.UpdateSettingRequest;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.github.leonardopinheirolacerda.akari.domain.setting.mapper.SettingMapper;
import io.github.leonardopinheirolacerda.akari.domain.setting.model.Setting;
import io.quarkus.hibernate.orm.panache.Panache;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.util.List;

@ApplicationScoped
public class SettingService {

    @Inject
    SettingMapper settingMapper;

    public List<SettingResponse> listSettings(String name) {
        final List<Setting> settings = name == null
                ? Setting.listAll()
                : Setting.findByName(name);

        final List<SettingResponse> responses = settingMapper.toResponseList(settings);

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

        final SettingResponse response = settingMapper.toResponse(setting);
        maskSecretValue(response);
        return response;
    }

    public Duration getAnilistCacheTtl() {
        return Duration.parse(findOrThrow("anilist.cache.ttl").value);
    }

    public Duration getTmdbCacheTtl() {
        return Duration.parse(findOrThrow("tmdb.cache.ttl").value);
    }

    public String getTmdbApiKey() {
        return findOrThrow("tmdb.api-key").value;
    }

    public String getTmdbDefaultLanguage() {
        return findOrThrow("tmdb.default-language").value;
    }

    public boolean isTmdbIncludeAdult() {
        return Boolean.parseBoolean(findOrThrow("tmdb.include-adult").value);
    }

    public Long getCacheMaxSizeBytes() {
        return Long.parseLong(findOrThrow("cache.max-size-bytes").value);
    }

    public String getCacheDir() {
        return findOrThrow("cache.dir").value;
    }

    public String getPlaybackTranscodeHardwareAcceleration() {
        return findOrThrow("playback.transcode.hardware-acceleration").value;
    }

    public Duration getPlaybackSessionHeartbeatTtl() {
        return Duration.parse(findOrThrow("playback.session.heartbeat-ttl").value);
    }

    public String getPlaybackWorkBaseDir() {
        return findOrThrow("playback.work.base-dir").value;
    }

    private Setting findOrThrow(String key) {
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