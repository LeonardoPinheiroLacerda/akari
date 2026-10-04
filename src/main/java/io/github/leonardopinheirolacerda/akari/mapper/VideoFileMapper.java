package io.github.leonardopinheirolacerda.akari.mapper;

import io.github.leonardopinheirolacerda.akari.api.dto.VideoFilePage;
import io.github.leonardopinheirolacerda.akari.api.dto.VideoFileResponse;
import io.github.leonardopinheirolacerda.akari.model.VideoFile;
import io.github.leonardopinheirolacerda.akari.model.pageable.PageResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "cdi")
public interface VideoFileMapper {

    @Mapping(source = "folder.id", target = "folderId")
    VideoFileResponse toResponse(VideoFile videoFile);

    VideoFilePage toPage(PageResult<VideoFile> pageResult);

}
