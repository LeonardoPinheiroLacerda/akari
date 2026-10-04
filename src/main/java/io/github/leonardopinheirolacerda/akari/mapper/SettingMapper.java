package io.github.leonardopinheirolacerda.akari.mapper;

import io.github.leonardopinheirolacerda.akari.api.dto.SettingResponse;
import io.github.leonardopinheirolacerda.akari.model.Setting;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "cdi")
public interface SettingMapper {

    SettingResponse toResponse(Setting setting);

    List<SettingResponse> toResponseList(List<Setting> settings);

}
