package io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums;

/**
 * Tipo de um evento registrado durante a execução de uma {@link IngestionStep} — compõe a linha
 * do tempo de cada tentativa.
 */
public enum IngestionEventType {

    /** A execução da etapa começou. */
    STARTED,

    /** Requisição enviada a uma integração externa (AniList, TMDB). */
    REQUEST_SENT,

    /** Resposta recebida da integração externa. */
    RESPONSE_RECEIVED,

    /** A busca devolveu candidatos. */
    RESULTS_FOUND,

    /** Um candidato foi escolhido. */
    MATCH_SELECTED,

    /** Nenhum candidato serviu. */
    MATCH_NOT_FOUND,

    /** Problema que não interrompe a etapa. */
    WARNING,

    /** Falha da etapa. */
    ERROR,

    /** A execução da etapa terminou. */
    COMPLETED
}
