package io.github.leonardopinheirolacerda.akari.resources;

import io.github.leonardopinheirolacerda.akari.api.SettingsApi;
import io.github.leonardopinheirolacerda.akari.api.dto.SettingResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.UpdateSettingRequest;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.util.List;

public class SettingsResource implements SettingsApi {

    @Override
    public List<SettingResponse> listSettings(String name) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    @Override
    public SettingResponse updateSetting(String key, UpdateSettingRequest updateSettingRequest) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }
}
