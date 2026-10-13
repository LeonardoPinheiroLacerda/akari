# cache

## Objetivo do módulo

Fila serial (1 slot) de download de arquivos de vídeo remotos para um diretório local, com eviction LRU por orçamento de espaço, e acompanhamento de progresso em tempo real via SSE.

## Responsabilidade principal

Enfileirar, processar e listar o cache local de `VideoFile`s cujo provider é remoto. Não baixa pelo HTTP diretamente — delega o download a um job assíncrono do rclone (via `StorageProviderClient`/`RcloneClient`) e só reconcilia o status dessa linha com o status do job.

Este pacote também hospeda `IntegrationCache` (`model/IntegrationCache.java`), que é um domínio **distinto** e sem relação funcional com a fila de vídeo: é um cache genérico de chave/valor para respostas upstream de AniList/TMDB (cache-aside via `CacheUtils.resolve`, consumido por `AnilistSearchService`/`TmdbService`). Convive no mesmo pacote só porque ambos são "cache" no sentido amplo — não compartilham model, service, nem mapper com `VideoFileCache`.

## Funcionalidades existentes

- `POST /v1/cache/items` — enfileira o download de um `VideoFile` remoto (`CacheResource.queueCacheDownload` → `CacheService.queueCacheDownload`).
- `GET /v1/cache/items` — pagina as linhas do cache, com filtro opcional `pendingOnly` (`CacheResource.listCaches` → `CacheService.listCaches`).
- `GET /v1/cache/events` — stream SSE de progresso (`CacheEventResource.subscribe` → `CacheEventBroadcaster.subscribe`), code-first (não gerado do contrato — o gerador jaxrs-spec não descreve SSE).
- Motor de processamento da fila, disparado por scheduler (`CacheScheduler` a cada 5s) — reconcilia downloads em andamento, inicia o próximo da fila, aplica eviction LRU (`CacheQueueProcessorService.processQueue`).
- Polling de progresso do download ativo, disparado por scheduler (`CacheScheduler` a cada 1s), só quando há inscritos SSE (`CacheQueueProcessorService.pollProgress`).
- Cache-aside genérico de respostas upstream (`IntegrationCache.find/readEntry/writeEntry`), sem relação com a fila de vídeo.

## Dependências internas e externas

- `domain/setting` (`SettingService`) — lê `cache.dir` e o orçamento máximo de espaço.
- `domain/storageprovider` (`StorageProvider`, `StorageProviderClientFactory`, `StorageProviderClient`, `RcloneClient` e DTOs do rclone) — resolve o client do provider remoto e dispara/consulta o job de download.
- `domain/videofile` (`VideoFile`, `VideoFileMapper`) — a entidade cacheada e o mapper usado para resolver `videoFile` no DTO de resposta.
- `exceptions/` (`BusinessRuleException`, `IntegrationNotConfiguredException`, `ResourceNotFoundException`) — erros de negócio traduzidos em HTTP pelo `ExceptionMapper` do projeto (fora deste domínio).
- Jakarta: `@Entity`/Panache (`VideoFileCache`, `IntegrationCache`), `jakarta.ws.rs.sse` (SSE), `@Scheduled` (Quarkus scheduler).

## Módulos relacionados

- `utils/VideoFileLocationUtils` — não faz parte deste domínio, mas importa `VideoFileCache` diretamente para resolver o caminho em disco de um `VideoFile` (local direto, ou via cache se remoto e `COMPLETED`). É o ponto de acoplamento real entre este domínio e `playback`/`mediaprobe`.
- `domain/playback` (`PlaybackService`) e `domain/mediaprobe` (`MediaProbeService`) — consomem `cache` indiretamente, só através de `VideoFileLocationUtils`; nenhum dos dois importa `io.github.leonardopinheirolacerda.akari.domain.cache` diretamente.
- `domain/anilist` (`AnilistSearchService`) e `domain/tmdb` (`TmdbService`) — únicos consumidores diretos de `IntegrationCache`, via `utils/CacheUtils.resolve`.

## Pontos de entrada

- `resource/CacheResource` — implementa `CacheApi` (gerado do contrato), expõe `/v1/cache/items`.
- `resource/CacheEventResource` — code-first, expõe `/v1/cache/events` (SSE), sem interface gerada.
- `scheduler/CacheScheduler` — único disparador do motor da fila (`CacheQueueProcessorService`); nada externo chama o processor diretamente.

## Fluxos importantes

**Enfileiramento** (`CacheService.queueCacheDownload`): valida que o `VideoFile` existe, que não há linha ativa (`QUEUED`/`DOWNLOADING`/`COMPLETED` — bloqueiam; `FAILED` é reaproveitada com o mesmo `id`), que o provider não é `LOCAL`, e que `cache.dir` está configurado. Persiste a linha como `QUEUED` e publica evento SSE `QUEUED`.

**Processamento da fila** (`CacheQueueProcessorService.processQueue`, a cada 5s):
1. `reconcileInProgress` — para cada linha `DOWNLOADING`, consulta o status do job rclone; se terminou, marca `COMPLETED`/`FAILED` e publica o evento correspondente.
2. `startNextIfIdle` — se o slot está livre, pega a linha `QUEUED` mais antiga, roda `evictForIncoming` (eviction LRU das linhas `COMPLETED` até abrir espaço pro tamanho do arquivo entrante, mesmo que ultrapasse o orçamento sozinho) e dispara o download via `StorageProviderClient.downloadFile`.
3. Se a fila transicionou de "tinha algo ativo" para "nada ativo", publica `IDLE` e fecha todos os sinks SSE.

**Progresso** (`CacheQueueProcessorService.pollProgress`, a cada 1s): só executa se há sinks SSE abertos; consulta `RcloneClient.getTransferProgress` da linha `DOWNLOADING` e publica `PROGRESS`.

**SSE** (`CacheEventBroadcaster`): a instância de `Sse` chega só via `@Context` no primeiro `subscribe` (não é injetável direto num bean `@ApplicationScoped`) e é capturada ali. Inscrição sem nada ativo recebe `IDLE` e é fechada na hora — nunca mantém conexão aberta sem nada a observar.

## Arquivos críticos

- `service/CacheQueueProcessorService.java` — motor da fila: reconciliação, início de download, eviction LRU. A lógica de maior risco do domínio.
- `service/CacheService.java` — regras de enfileiramento/listagem voltadas ao resource.
- `model/VideoFileCache.java` — entidade e todas as queries Panache usadas pelos dois services acima.
- `sse/CacheEventBroadcaster.java` — ciclo de vida dos sinks SSE; bugs aqui vazam conexões ou perdem eventos.
- `model/IntegrationCache.java` — cache key/value genérico; mexer aqui afeta `anilist` e `tmdb`, não a fila de vídeo.

## Observações técnicas e débitos identificados

- `IntegrationCache` está dentro de `domain/cache/model`, mas é um domínio funcionalmente independente de `VideoFileCache` (confirmado lendo o código e o Javadoc da própria classe, que já avisa "não tem relação"). **Backlog real** (confirmado pelo mantenedor): candidato a pacote próprio (ex. `domain/integrationcache`) numa futura reorganização, já que hoje "cache" mistura dois domínios sem service, mapper ou resource compartilhado.
- `CacheEventResource` de fato não implementa nenhuma interface `*Api` gerada — é code-first puro, como a descrição da tag `Cache` no `openapi.yaml` antecipa (SSE não é modelável pelo gerador jaxrs-spec).
- A fila é serial por construção (sempre no máximo uma linha `DOWNLOADING` — `startNextIfIdle` não inicia um novo download enquanto `findDownloading()` não estiver vazio), mas isso não é imposto por nenhum lock de banco, só pela ordem de execução do `CacheScheduler` com `ConcurrentExecution.SKIP`.
- `evictForIncoming` prossegue mesmo quando um único arquivo entrante excede o orçamento total configurado, apenas logando um aviso — não há hard-limit nem rejeição do enfileiramento nesse caso. **Intencional** (confirmado pelo mantenedor): conseguir assistir é prioridade sobre respeitar o orçamento à risca.
- `CacheQueueProcessorService` e `CacheService` são duas facetas do mesmo `VideoFileCache` por decisão deliberada de code style (documentado no Javadoc de ambos), não uma duplicação acidental.
- Dependência real `playback`/`mediaprobe` → `cache` passa por `utils/VideoFileLocationUtils`, não por chamada direta a `CacheService`/`VideoFileCache` — útil ter em mente ao avaliar impacto de mudanças neste domínio.
