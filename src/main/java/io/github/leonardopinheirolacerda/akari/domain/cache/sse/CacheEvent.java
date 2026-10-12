package io.github.leonardopinheirolacerda.akari.domain.cache.sse;

import io.github.leonardopinheirolacerda.akari.api.dto.CacheItem;
import io.github.leonardopinheirolacerda.akari.api.dto.CacheStatus;
import java.util.List;
import java.util.UUID;

/**
 * Payload de um evento do stream SSE {@code /v1/cache/events}, publicado pelo
 * {@link CacheEventBroadcaster}.
 *
 * <p>Os campos usados variam por {@link CacheEventType}: {@code SNAPSHOT} só preenche
 * {@code items}; {@code IDLE} não preenche nenhum; {@code QUEUED}/{@code STARTED}/
 * {@code COMPLETED} preenchem {@code id} e {@code status}; {@code FAILED} soma
 * {@code errorMessage}; {@code PROGRESS} preenche {@code id}, {@code status} e as métricas de
 * transferência; {@code EVICTED} só preenche {@code id}.
 *
 * @param type tipo do evento, decide quais dos demais campos vêm preenchidos
 * @param id id da linha de cache a que o evento se refere; {@code null} em {@code SNAPSHOT}/
 *           {@code IDLE}
 * @param status status da linha no momento do evento; {@code null} em {@code SNAPSHOT}/
 *               {@code IDLE}/{@code EVICTED}
 * @param errorMessage motivo da falha; só preenchido em {@code FAILED}
 * @param bytes bytes já transferidos; só preenchido em {@code PROGRESS}
 * @param totalBytes total esperado de bytes; só preenchido em {@code PROGRESS}
 * @param speed velocidade instantânea em bytes/segundo; só preenchido em {@code PROGRESS}
 * @param eta segundos restantes estimados; só preenchido em {@code PROGRESS}
 * @param percentage percentual concluído da transferência; só preenchido em {@code PROGRESS}
 * @param items linhas ativas no momento da inscrição; só preenchido em {@code SNAPSHOT}
 */
public record CacheEvent(
        CacheEventType type,
        UUID id,
        CacheStatus status,
        String errorMessage,
        Long bytes,
        Long totalBytes,
        Double speed,
        Long eta,
        Double percentage,
        List<CacheItem> items
) {

    /**
     * Monta o evento enviado a um cliente SSE que acabou de se inscrever, com linhas ativas.
     *
     * @param items linhas atualmente {@code QUEUED}/{@code DOWNLOADING}
     * @return evento {@code SNAPSHOT} com as linhas informadas
     */
    public static CacheEvent snapshot(List<CacheItem> items) {
        return new CacheEvent(CacheEventType.SNAPSHOT, null, null, null, null, null, null, null, null, items);
    }

    /**
     * Monta o evento enviado quando não há nada ativo na fila — seja na inscrição sem nada pra
     * observar, seja quando a fila termina de drenar.
     *
     * @return evento {@code IDLE}, sem nenhum outro campo preenchido
     */
    public static CacheEvent idle() {
        return new CacheEvent(CacheEventType.IDLE, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Monta o evento de uma linha recém-enfileirada (nova ou reaproveitada de um
     * {@code FAILED}).
     *
     * @param id id da linha enfileirada
     * @return evento {@code QUEUED} com status {@code QUEUED}
     */
    public static CacheEvent queued(UUID id) {
        return new CacheEvent(CacheEventType.QUEUED, id, CacheStatus.QUEUED, null, null, null, null, null, null, null);
    }

    /**
     * Monta o evento de uma linha que acabou de começar a baixar.
     *
     * @param id id da linha que iniciou o download
     * @return evento {@code STARTED} com status {@code DOWNLOADING}
     */
    public static CacheEvent started(UUID id) {
        return new CacheEvent(CacheEventType.STARTED, id, CacheStatus.DOWNLOADING, null, null, null, null, null, null, null);
    }

    /**
     * Monta o evento periódico de progresso de um download em andamento.
     *
     * @param id id da linha em download
     * @param bytes bytes já transferidos
     * @param totalBytes total esperado de bytes
     * @param speed velocidade instantânea em bytes/segundo
     * @param eta segundos restantes estimados; {@code null} enquanto indeterminado
     * @param percentage percentual concluído, entre {@code 0.0} e {@code 100.0}
     * @return evento {@code PROGRESS} com status {@code DOWNLOADING} e as métricas informadas
     */
    public static CacheEvent progress(UUID id, Long bytes, Long totalBytes, Double speed, Long eta, Double percentage) {
        return new CacheEvent(CacheEventType.PROGRESS, id, CacheStatus.DOWNLOADING, null, bytes, totalBytes, speed, eta, percentage, null);
    }

    /**
     * Monta o evento de uma linha cujo download terminou com sucesso.
     *
     * @param id id da linha concluída
     * @return evento {@code COMPLETED} com status {@code COMPLETED}
     */
    public static CacheEvent completed(UUID id) {
        return new CacheEvent(CacheEventType.COMPLETED, id, CacheStatus.COMPLETED, null, null, null, null, null, null, null);
    }

    /**
     * Monta o evento de uma linha cujo download falhou, seja por erro do rclone, seja porque o
     * arquivo saiu do catalog antes do início.
     *
     * @param id id da linha que falhou
     * @param errorMessage motivo da falha, pra exibir ao consumidor do stream
     * @return evento {@code FAILED} com status {@code FAILED} e a mensagem informada
     */
    public static CacheEvent failed(UUID id, String errorMessage) {
        return new CacheEvent(CacheEventType.FAILED, id, CacheStatus.FAILED, errorMessage, null, null, null, null, null, null);
    }

    /**
     * Monta o evento de uma linha removida pela eviction LRU pra abrir espaço a um novo
     * download.
     *
     * @param id id da linha evictada (já removida do banco e, se possível, do disco)
     * @return evento {@code EVICTED}, sem status (a linha não existe mais)
     */
    public static CacheEvent evicted(UUID id) {
        return new CacheEvent(CacheEventType.EVICTED, id, null, null, null, null, null, null, null, null);
    }

}
