package io.github.leonardopinheirolacerda.akari.domain.anilist.client;

/**
 * Strings de query GraphQL enviadas à AniList.
 */
public final class AnilistQueries {

    /**
     * Query de search paginada — omite {@code relations} pra evitar payload explosivo.
     */
    public static final String MEDIA_SEARCH = """
            query ($search: String!, $page: Int!, $perPage: Int!) {
              Page(page: $page, perPage: $perPage) {
                pageInfo { total perPage currentPage lastPage hasNextPage }
                media(search: $search, type: ANIME) {
                  id
                  title { romaji english native userPreferred }
                  synonyms
                  coverImage { medium large extraLarge }
                  description(asHtml: true)
                  episodes
                  duration
                  startDate { year month day }
                  seasonYear
                  format
                  season
                  averageScore
                  isAdult
                  genres
                }
              }
            }
            """;

    /**
     * Query de fetch por id — traz o shape completo, incluindo {@code relations}.
     */
    public static final String MEDIA_BY_ID = """
            query ($id: Int!) {
              Media(id: $id, type: ANIME) {
                id
                title { romaji english native userPreferred }
                synonyms
                coverImage { medium large extraLarge }
                description(asHtml: true)
                episodes
                duration
                startDate { year month day }
                seasonYear
                format
                season
                averageScore
                isAdult
                genres
                relations {
                  edges {
                    relationType(version: 2)
                    node {
                      id
                      type
                      format
                      title { romaji english native userPreferred }
                    }
                  }
                }
              }
            }
            """;

    private AnilistQueries() {
    }

}
