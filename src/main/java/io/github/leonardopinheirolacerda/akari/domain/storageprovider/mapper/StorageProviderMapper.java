package io.github.leonardopinheirolacerda.akari.domain.storageprovider.mapper;

import io.github.leonardopinheirolacerda.akari.api.dto.StorageProviderPage;
import io.github.leonardopinheirolacerda.akari.api.dto.StorageProviderResponse;
import io.github.leonardopinheirolacerda.akari.model.PageResult;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.model.StorageProvider;
import org.mapstruct.Mapper;

/**
 * Converte {@link StorageProvider} (model) pros DTOs gerados do contrato ({@code
 * StorageProviderResponse}, {@code StorageProviderPage}). Nunca expõe {@code config} — o DTO de
 * resposta não tem esse campo, pra não vazar credenciais (mesmo obscurecidas).
 */
@Mapper(componentModel = "cdi")
public interface StorageProviderMapper {

    StorageProviderResponse toResponse(StorageProvider storageProvider);

    StorageProviderPage toPage(PageResult<StorageProvider> pageResult);

}