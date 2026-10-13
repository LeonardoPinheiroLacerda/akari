package io.github.leonardopinheirolacerda.akari.domain.mediafolder.mapper;

import io.github.leonardopinheirolacerda.akari.api.dto.MediaFolderPage;
import io.github.leonardopinheirolacerda.akari.api.dto.MediaFolderResponse;
import io.github.leonardopinheirolacerda.akari.domain.mediafolder.model.MediaFolder;
import io.github.leonardopinheirolacerda.akari.model.PageResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Converte {@link MediaFolder} (model) para os DTOs gerados do contrato ({@code
 * MediaFolderResponse}, {@code MediaFolderPage}), resolvendo os ids das associações lazy
 * ({@code storageProvider}, {@code parent}).
 */
@Mapper(componentModel = "cdi")
public interface MediaFolderMapper {

    @Mapping(source = "storageProvider.id", target = "storageProviderId")
    @Mapping(source = "parent.id", target = "parentId")
    @Mapping(target = "hasFiles", expression = "java(mediaFolder.hasFiles())")
    MediaFolderResponse toResponse(MediaFolder mediaFolder);

    MediaFolderPage toPage(PageResult<MediaFolder> pageResult);

}
