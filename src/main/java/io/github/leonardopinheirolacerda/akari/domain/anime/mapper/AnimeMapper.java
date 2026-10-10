package io.github.leonardopinheirolacerda.akari.domain.anime.mapper;

import io.github.leonardopinheirolacerda.akari.api.dto.AnimeBindingSummary;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimePage;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeThumbnailsResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeTitlesResponse;
import io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos.AnilistMediaResponse;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.model.PageResult;
import io.github.leonardopinheirolacerda.akari.utils.TmdbImageUtils;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Converte entre o model {@link Anime}, a resposta da AniList usada pra popular seus campos, e
 * os DTOs gerados do contrato.
 */
@Mapper(componentModel = "cdi")
public interface AnimeMapper {

    @Mapping(source = "seasonYear", target = "year")
    @Mapping(target = "titles", expression = "java(toTitles(anime))")
    @Mapping(target = "synopsis", expression = "java(toSynopsis(anime))")
    @Mapping(target = "thumbnails", expression = "java(toThumbnails(anime))")
    @Mapping(target = "banner", expression = "java(toBanner(anime))")
    @Mapping(target = "logo", expression = "java(toLogo(anime))")
    AnimeResponse toView(Anime anime);

    @Mapping(source = "folder.id", target = "folderId")
    @Mapping(target = "title", expression = "java(toDisplayTitle(anime))")
    @Mapping(target = "thumbnail", expression = "java(toDisplayThumbnail(anime))")
    @Mapping(source = "seasonYear", target = "year")
    @Mapping(target = "hasTmdb", expression = "java(anime.tmdbId != null)")
    AnimeBindingSummary toBindingSummary(Anime anime);

    List<AnimeBindingSummary> toBindingSummaryList(List<Anime> animes);

    AnimePage toAnimePage(PageResult<Anime> pageResult);

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

    // override de título troca só o main — english/japanese/synonyms continuam da AniList
    default AnimeTitlesResponse toTitles(Anime anime) {
        return new AnimeTitlesResponse()
                .main(toDisplayTitle(anime))
                .english(anime.titleEnglish)
                .japanese(anime.titleJapanese)
                .synonyms(anime.titleSynonyms);
    }

    default String toSynopsis(Anime anime) {
        return hasText(anime.overrideSynopsis) ? anime.overrideSynopsis : anime.synopsis;
    }

    // override de poster troca os 3 tamanhos juntos, a partir do mesmo filePath do TMDB
    default AnimeThumbnailsResponse toThumbnails(Anime anime) {
        if (hasText(anime.overridePosterPath)) {
            return new AnimeThumbnailsResponse()
                    .small(TmdbImageUtils.posterSmall(anime.overridePosterPath))
                    .medium(toDisplayThumbnail(anime))
                    .large(TmdbImageUtils.posterLarge(anime.overridePosterPath));
        }

        return new AnimeThumbnailsResponse()
                .small(anime.thumbnailSmall)
                .medium(toDisplayThumbnail(anime))
                .large(anime.thumbnailLarge);
    }

    // título/poster em resolução média exibidos fora do AnimeResponse (binding summary,
    // nó do grafo de relações) — mesma regra de override, reaproveitada nos dois lugares
    default String toDisplayTitle(Anime anime) {
        return hasText(anime.overrideTitle) ? anime.overrideTitle : anime.titleMain;
    }

    default String toDisplayThumbnail(Anime anime) {
        return hasText(anime.overridePosterPath)
                ? TmdbImageUtils.posterMedium(anime.overridePosterPath)
                : anime.thumbnailMedium;
    }

    // banner/logo só existem via override manual — sem TMDB + override, ficam null
    default String toBanner(Anime anime) {
        return TmdbImageUtils.backdrop(anime.overrideBackdropPath);
    }

    default String toLogo(Anime anime) {
        return TmdbImageUtils.logo(anime.overrideLogoPath);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
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
