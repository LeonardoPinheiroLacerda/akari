package io.github.leonardopinheirolacerda.akari.mapper;

import io.github.leonardopinheirolacerda.akari.clients.rclone.dtos.RcloneItemResponse;
import io.github.leonardopinheirolacerda.akari.providers.models.StorageItem;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "cdi")
public interface StorageItemMapper {

    StorageItem toStorageItem(RcloneItemResponse item);

    List<StorageItem> toStorageItems(List<RcloneItemResponse> items);

}