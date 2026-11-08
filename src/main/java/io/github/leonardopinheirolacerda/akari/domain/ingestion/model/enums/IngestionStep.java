package io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums;

/**
 * Etapas do processamento de uma pasta na ingestão, na ordem em que se encadeiam.
 *
 * <p>Nenhuma etapa é executada hoje: a ingestão só registra pastas, arquivos e tasks.
 */
public enum IngestionStep {

    /** Vincula a pasta a um anime da AniList. */
    ANILIST_SYNC,

    /** Vincula o anime a um título do TMDB. */
    TMDB_BIND,

    /** Preenche os overrides de imagem do anime a partir do TMDB. */
    TMDB_OVERRIDES,

    /** Pareia os arquivos de vídeo da pasta com os episódios do TMDB. */
    TMDB_FILEPAIR
}
