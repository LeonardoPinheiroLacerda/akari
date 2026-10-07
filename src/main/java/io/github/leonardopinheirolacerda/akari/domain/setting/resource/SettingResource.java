package io.github.leonardopinheirolacerda.akari.domain.setting.resource;

import io.github.leonardopinheirolacerda.akari.api.SettingApi;
import io.github.leonardopinheirolacerda.akari.api.dto.SettingResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.UpdateSettingRequest;
import io.github.leonardopinheirolacerda.akari.domain.setting.service.SettingService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class SettingResource implements SettingApi {

    @Inject
    SettingService settingService;

    @Override
    public List<SettingResponse> listSettings(String name) {
        return settingService.listSettings(name);
    }

    @Override
    public SettingResponse updateSetting(String key, UpdateSettingRequest updateSettingRequest) {
        return settingService.updateSetting(key, updateSettingRequest);
    }
}
