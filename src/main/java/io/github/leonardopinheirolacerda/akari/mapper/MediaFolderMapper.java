package io.github.leonardopinheirolacerda.akari.mapper;

import io.github.leonardopinheirolacerda.akari.api.dto.MediaFolderPage;
import io.github.leonardopinheirolacerda.akari.api.dto.MediaFolderResponse;
import io.github.leonardopinheirolacerda.akari.model.MediaFolder;
import io.github.leonardopinheirolacerda.akari.model.pageable.PageResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "cdi")
public interface MediaFolderMapper {

    @Mapping(source = "storageProvider.id", target = "storageProviderId")
    @Mapping(source = "parent.id", target = "parentId")
    MediaFolderResponse toResponse(MediaFolder mediaFolder);

    MediaFolderPage toPage(PageResult<MediaFolder> pageResult);

}
