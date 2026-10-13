package io.github.leonardopinheirolacerda.akari.domain.notification.mapper;

import io.github.leonardopinheirolacerda.akari.api.dto.NotificationPage;
import io.github.leonardopinheirolacerda.akari.api.dto.NotificationResponse;
import io.github.leonardopinheirolacerda.akari.domain.notification.model.Notification;
import io.github.leonardopinheirolacerda.akari.model.PageResult;
import org.mapstruct.Mapper;

/**
 * Converte {@link Notification} (model) para os DTOs gerados do contrato ({@code
 * NotificationResponse}, {@code NotificationPage}). Cópia direta de campos, sem conversão
 * não-trivial.
 */
@Mapper(componentModel = "cdi")
public interface NotificationMapper {

    NotificationResponse toResponse(Notification notification);

    NotificationPage toPage(PageResult<Notification> pageResult);

}
