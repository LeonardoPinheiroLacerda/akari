package io.github.leonardopinheirolacerda.akari.domain.cache.mapper;

import io.github.leonardopinheirolacerda.akari.api.dto.CacheItem;
import io.github.leonardopinheirolacerda.akari.api.dto.CacheItemPage;
import io.github.leonardopinheirolacerda.akari.domain.cache.model.VideoFileCache;
import io.github.leonardopinheirolacerda.akari.domain.videofile.mapper.VideoFileMapper;
import io.github.leonardopinheirolacerda.akari.model.PageResult;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * {@code videoFileMapper} entra como {@code @Context} (não {@code uses}) porque o MapStruct
 * gera o campo de uma dependência de {@code uses} com {@code javax.inject.Inject}, que o Arc do
 * Quarkus rejeita (espera {@code jakarta.inject.Inject}). Como {@code @Context}, quem chama
 * injeta e repassa a instância, sem gerar injeção própria.
 *
 * <p>O mapeamento de {@code videoFile} é forçado via {@code expression} pra delegar no
 * {@link VideoFileMapper#toResponse} (que resolve {@code folderId} a partir de
 * {@code folder.id}) — sem isso o MapStruct gera uma conversão própria, ingênua, que deixa
 * {@code folderId} nulo.
 */
@Mapper(componentModel = "cdi")
public interface CacheMapper {

    @Mapping(target = "videoFile", expression = "java(videoFileMapper.toResponse(videoFileCache.videoFile))")
    CacheItem toItem(VideoFileCache videoFileCache, @Context VideoFileMapper videoFileMapper);

    CacheItemPage toPage(PageResult<VideoFileCache> pageResult, @Context VideoFileMapper videoFileMapper);

}
