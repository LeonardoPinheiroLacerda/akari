package io.github.leonardopinheirolacerda.akari.domain.setting.mapper;

import io.github.leonardopinheirolacerda.akari.api.dto.SettingResponse;
import io.github.leonardopinheirolacerda.akari.domain.setting.model.Setting;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Converte {@link Setting} (model) pro DTO de resposta do contrato ({@code SettingResponse}).
 *
 * <p>O mascaramento de preferências {@code secret} não é feito aqui — fica a cargo de quem chama
 * o mapper, depois da conversão.
 */
@Mapper(componentModel = "cdi")
public interface SettingMapper {

    SettingResponse toResponse(Setting setting);

    List<SettingResponse> toResponseList(List<Setting> settings);

}
