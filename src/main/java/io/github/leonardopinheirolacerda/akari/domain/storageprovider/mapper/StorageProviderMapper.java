package io.github.leonardopinheirolacerda.akari.domain.storageprovider.mapper;

import io.github.leonardopinheirolacerda.akari.api.dto.StorageProviderPage;
import io.github.leonardopinheirolacerda.akari.api.dto.StorageProviderResponse;
import io.github.leonardopinheirolacerda.akari.model.PageResult;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.model.StorageProvider;
import org.mapstruct.Mapper;

@Mapper(componentModel = "cdi")
public interface StorageProviderMapper {

    StorageProviderResponse toResponse(StorageProvider storageProvider);

    StorageProviderPage toPage(PageResult<StorageProvider> pageResult);

}