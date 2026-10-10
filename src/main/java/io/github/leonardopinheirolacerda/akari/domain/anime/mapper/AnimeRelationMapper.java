package io.github.leonardopinheirolacerda.akari.domain.anime.mapper;

import io.github.leonardopinheirolacerda.akari.api.dto.AnimeRelationEdge;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeRelationNode;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeRootKind;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.AnimeRelation;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Converte entre o model {@link AnimeRelation}, o tipo bruto de relação da AniList, e os DTOs
 * gerados do contrato pro grafo de relações.
 */
@Mapper(componentModel = "cdi")
public interface AnimeRelationMapper {

    @Mapping(source = "fromAnilistId", target = "from")
    @Mapping(source = "toAnilistId", target = "to")
    AnimeRelationEdge toEdge(AnimeRelation relation);

    List<AnimeRelationEdge> toEdgeList(List<AnimeRelation> relations);

    /**
     * @param anime anime a representar como nó
     * @param title título já resolvido (considera override TMDB) — ver
     *              {@code AnimeOverrideService#resolveTitle}
     * @param posterUrl poster já resolvido (considera override TMDB) — ver
     *                  {@code AnimeOverrideService#resolveThumbnailMedium}
     * @param rootKind classificação de raiz já resolvida pelo grafo
     */
    @Mapping(source = "anime.anilistId", target = "anilistId")
    @Mapping(source = "title", target = "title")
    @Mapping(source = "posterUrl", target = "posterUrl")
    @Mapping(source = "anime.folder.id", target = "folderId")
    @Mapping(source = "rootKind", target = "rootKind")
    AnimeRelationNode toNode(Anime anime, String title, String posterUrl, AnimeRootKind rootKind);

}
