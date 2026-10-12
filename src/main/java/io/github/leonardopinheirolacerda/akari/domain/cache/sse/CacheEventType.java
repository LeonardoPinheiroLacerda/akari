package io.github.leonardopinheirolacerda.akari.domain.cache.sse;

/**
 * Tipo de um evento publicado no stream SSE {@code /v1/cache/events}.
 */
public enum CacheEventType {

    /** Estado inicial enviado a um cliente que acabou de se inscrever, com linhas ativas. */
    SNAPSHOT,

    /** Enviado quando não há nada ativo na fila — na inscrição ou ao fim do processamento. */
    IDLE,

    /** Uma linha acabou de ser enfileirada (nova ou reaproveitada de um {@code FAILED}). */
    QUEUED,

    /** Uma linha acabou de começar a baixar. */
    STARTED,

    /** Progresso periódico do download em andamento. */
    PROGRESS,

    /** Uma linha terminou de baixar com sucesso. */
    COMPLETED,

    /** Uma linha falhou — erro do rclone ou arquivo removido do catalog antes do início. */
    FAILED,

    /** Uma linha foi removida pela eviction LRU pra abrir espaço a um novo download. */
    EVICTED

}
