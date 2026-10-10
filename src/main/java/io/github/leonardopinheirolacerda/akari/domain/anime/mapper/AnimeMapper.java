package io.github.leonardopinheirolacerda.akari.domain.anime.mapper;

import io.github.leonardopinheirolacerda.akari.api.dto.AnimeBindingSummary;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeThumbnailsResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeTitlesResponse;
import io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos.AnilistMediaResponse;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Converte entre o model {@link Anime}, a resposta da AniList usada pra popular seus campos, e
 * os DTOs gerados do contrato. Só cópia de campos — a resolução de overrides TMDB é regra de
 * negócio e mora no {@code AnimeOverrideService}, não aqui.
 */
@Mapper(componentModel = "cdi")
public interface AnimeMapper {

    @Mapping(source = "seasonYear", target = "year")
    @Mapping(target = "titles", expression = "java(toTitles(anime))")
    @Mapping(target = "thumbnails", expression = "java(toThumbnails(anime))")
    @Mapping(target = "banner", ignore = true)
    @Mapping(target = "logo", ignore = true)
    AnimeResponse toView(Anime anime);

    @Mapping(source = "folder.id", target = "folderId")
    @Mapping(source = "titleMain", target = "title")
    @Mapping(source = "thumbnailMedium", target = "thumbnail")
    @Mapping(source = "seasonYear", target = "year")
    @Mapping(target = "hasTmdb", expression = "java(anime.tmdbId != null)")
    AnimeBindingSummary toBindingSummary(Anime anime);

    /**
     * Copia os campos de metadata da resposta da AniList pro model, usado tanto no binding
     * inicial quanto no sync. Não toca na pasta nem na curadoria manual.
     *
     * @param source resposta da AniList pro anime
     * @param anime model a ser atualizado
     */
    @Mapping(source = "id", target = "anilistId")
    @Mapping(source = "title.romaji", target = "titleMain")
    @Mapping(source = "title.english", target = "titleEnglish")
    @Mapping(source = "title.nativeTitle", target = "titleJapanese")
    @Mapping(source = "synonyms", target = "titleSynonyms")
    @Mapping(source = "description", target = "synopsis")
    @Mapping(source = "coverImage.medium", target = "thumbnailSmall")
    @Mapping(source = "coverImage.large", target = "thumbnailMedium")
    @Mapping(source = "coverImage.extraLarge", target = "thumbnailLarge")
    @Mapping(target = "duration", expression = "java(toDurationLabel(source.duration()))")
    @Mapping(target = "score", expression = "java(toScore(source.averageScore()))")
    @Mapping(target = "folder", ignore = true)
    @Mapping(target = "franchiseRootCuration", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "tmdbId", ignore = true)
    @Mapping(target = "tmdbMediaType", ignore = true)
    @Mapping(target = "tmdbBoundAt", ignore = true)
    @Mapping(target = "overrideTitle", ignore = true)
    @Mapping(target = "overrideSynopsis", ignore = true)
    @Mapping(target = "overridePosterPath", ignore = true)
    @Mapping(target = "overrideBackdropPath", ignore = true)
    @Mapping(target = "overrideLogoPath", ignore = true)
    void applyAnilistData(AnilistMediaResponse source, @MappingTarget Anime anime);

    default AnimeTitlesResponse toTitles(Anime anime) {
        return new AnimeTitlesResponse()
                .main(anime.titleMain)
                .english(anime.titleEnglish)
                .japanese(anime.titleJapanese)
                .synonyms(anime.titleSynonyms);
    }

    default AnimeThumbnailsResponse toThumbnails(Anime anime) {
        return new AnimeThumbnailsResponse()
                .small(anime.thumbnailSmall)
                .medium(anime.thumbnailMedium)
                .large(anime.thumbnailLarge);
    }

    // a AniList devolve a duração em minutos crus; o contrato pede formato livre pro consumidor
    default String toDurationLabel(Integer minutes) {
        if (minutes == null) {
            return null;
        }
        return minutes + " min";
    }

    // averageScore da AniList é 0-100; o contrato expõe em escala 0-10
    default Double toScore(Integer averageScore) {
        if (averageScore == null) {
            return null;
        }
        return averageScore / 10.0;
    }

}
