package io.github.leonardopinheirolacerda.akari.domain.anime.mapper;

import io.github.leonardopinheirolacerda.akari.api.dto.AnimeRelationEdge;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeRelationNode;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeRootKind;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.AnimeRelation;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.RelationType;
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
     *              {@link AnimeMapper#toDisplayTitle(Anime)}
     * @param posterUrl poster já resolvido (considera override TMDB) — ver
     *                  {@link AnimeMapper#toDisplayThumbnail(Anime)}
     * @param rootKind classificação de raiz já resolvida pelo grafo
     */
    @Mapping(source = "anime.anilistId", target = "anilistId")
    @Mapping(source = "title", target = "title")
    @Mapping(source = "posterUrl", target = "posterUrl")
    @Mapping(source = "anime.folder.id", target = "folderId")
    @Mapping(source = "rootKind", target = "rootKind")
    AnimeRelationNode toNode(Anime anime, String title, String posterUrl, AnimeRootKind rootKind);

    /**
     * Converte o tipo bruto de relação da AniList pro enum interno. Tipos sem relevância pro
     * grafo caem em {@link RelationType#OTHER}; valores desconhecidos em
     * {@link RelationType#UNKNOWN}.
     *
     * @param rawRelationType valor bruto (ex.: {@code SEQUEL}, {@code ADAPTATION})
     * @return o {@link RelationType} correspondente
     */
    default RelationType toRelationType(String rawRelationType) {
        if (rawRelationType == null) {
            return RelationType.UNKNOWN;
        }

        return switch (rawRelationType) {
            case "SEQUEL" -> RelationType.SEQUEL;
            case "PREQUEL" -> RelationType.PREQUEL;
            case "SIDE_STORY" -> RelationType.SIDE_STORY;
            case "PARENT" -> RelationType.PARENT;
            case "SUMMARY" -> RelationType.SUMMARY;
            case "ALTERNATIVE" -> RelationType.ALTERNATIVE;
            case "SPIN_OFF" -> RelationType.SPIN_OFF;
            case "OTHER", "ADAPTATION", "CHARACTER", "CONTAINS", "COMPILATION", "SOURCE" -> RelationType.OTHER;
            default -> RelationType.UNKNOWN;
        };
    }

}
