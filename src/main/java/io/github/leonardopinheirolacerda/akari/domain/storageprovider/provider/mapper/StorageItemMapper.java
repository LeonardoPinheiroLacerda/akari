package io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.mapper;

import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.dtos.RcloneItemResponse;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.model.StorageItem;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "cdi")
public interface StorageItemMapper {

    StorageItem toStorageItem(RcloneItemResponse item);

    List<StorageItem> toStorageItems(List<RcloneItemResponse> items);

}