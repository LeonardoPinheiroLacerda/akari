package io.github.leonardopinheirolacerda.akari.domain.smartlist.mapper;

import io.github.leonardopinheirolacerda.akari.api.dto.SmartListRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.SmartListResponse;
import io.github.leonardopinheirolacerda.akari.domain.smartlist.model.SmartList;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Converte entre o model {@link SmartList} e os DTOs gerados do contrato.
 */
@Mapper(componentModel = "cdi")
public interface SmartListMapper {

    SmartListResponse toResponse(SmartList smartList);

    List<SmartListResponse> toResponseList(List<SmartList> smartLists);

    /**
     * Copia o payload pro model, usado tanto na criação quanto na atualização.
     *
     * @param smartListRequest payload recebido
     * @param smartList model a ser preenchido
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateFromRequest(SmartListRequest smartListRequest, @MappingTarget SmartList smartList);

}
