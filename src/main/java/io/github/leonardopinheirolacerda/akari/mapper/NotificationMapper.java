package io.github.leonardopinheirolacerda.akari.mapper;

import io.github.leonardopinheirolacerda.akari.api.dto.NotificationResponse;
import io.github.leonardopinheirolacerda.akari.model.Notification;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "cdi")
public interface NotificationMapper {

    NotificationResponse toResponse(Notification notification);

    List<NotificationResponse> toResponseList(List<Notification> notifications);

}
