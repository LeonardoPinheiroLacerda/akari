package io.github.leonardopinheirolacerda.akari.domain.user.mapper;

import io.github.leonardopinheirolacerda.akari.api.dto.UserRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.UserResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.WatchProgress;
import io.github.leonardopinheirolacerda.akari.domain.user.model.User;
import io.github.leonardopinheirolacerda.akari.domain.user.model.WatchHistory;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Converte os models do domínio {@code user} ({@link User}, {@link WatchHistory}) para os DTOs
 * gerados do contrato.
 */
@Mapper(componentModel = "cdi")
public interface UserMapper {

    UserResponse toResponse(User user);

    List<UserResponse> toResponseList(List<User> users);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    User toModel(UserRequest userRequest);

    WatchProgress toWatchProgress(WatchHistory watchHistory);

}
