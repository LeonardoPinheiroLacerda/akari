package io.github.leonardopinheirolacerda.akari.domain.anilist.mapper;

import io.github.leonardopinheirolacerda.akari.api.dto.AnilistFormat;
import io.github.leonardopinheirolacerda.akari.api.dto.AnilistMediaSummary;
import io.github.leonardopinheirolacerda.akari.api.dto.AnilistSeason;
import io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos.AnilistMediaResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "cdi")
public interface AnilistMapper {

    @Mapping(source = "id", target = "anilistId")
    AnilistMediaSummary toSummary(AnilistMediaResponse media);

    List<AnilistMediaSummary> toSummaryList(List<AnilistMediaResponse> media);

    // format/season chegam como string crua da AniList; valor fora do enum vira UNKNOWN em vez
    // de estourar — é exatamente o que o UNKNOWN da whitelist existe pra cobrir.
    default AnilistFormat toFormat(String format) {
        if (format == null) {
            return null;
        }
        try {
            return AnilistFormat.valueOf(format);
        } catch (IllegalArgumentException e) {
            return AnilistFormat.UNKNOWN;
        }
    }

    default AnilistSeason toSeason(String season) {
        if (season == null) {
            return null;
        }
        try {
            return AnilistSeason.valueOf(season);
        } catch (IllegalArgumentException e) {
            return AnilistSeason.UNKNOWN;
        }
    }

}
