# utils

## Objetivo do módulo
Reunir helpers puros, sem estado, de uso geral entre domínios — lógica pequena e reutilizável que não justifica um service ou não pertence a um domínio específico.

## Responsabilidade principal
Oferecer funções estáticas, sem injeção CDI (todas as classes são `final` com construtor privado), para: cache-first genérico, manipulação de paths/nomes de arquivo, classificação de erro HTTP, ordenação paginada via Panache, retry com backoff, espera bloqueante sem propagar interrupção, montagem de URLs de imagem do TMDB e resolução de localização em disco de um `VideoFile`.

## Funcionalidades existentes
- **`CacheUtils`** — `buildKey(String...)` monta chave de cache normalizada (trim+lowercase, unida por `:`); `resolve(forceRefresh, ttl, cacheReader, loader, writer)` implementa o fluxo cache-first (serve do cache se fresco, senão busca no upstream e grava). Usa `model.CacheEntry`.
- **`DirectoryUtils`** — `parentOf`, `join`, `lastSegmentOf`, `depthOf` para paths relativos lógicos (sempre `/`, nunca `File.separator`) do catalog/storage provider.
- **`FileNameUtils`** — `extensionOf(name)` e `isVideoFile(name)` (checa extensão contra whitelist: mkv, mp4, avi, mov, wmv, webm, ts, m2ts, flv), usado pela ingestão.
- **`HttpErrorUtils`** — `isNotFound(Exception)` identifica se uma falha de REST client é um `404` upstream.
- **`PaginationUtils`** — `toSort(sort, sortableFields)` converte specs `campo[:asc|desc]` da API num `Sort` do Panache, validando contra whitelist do chamador; lança `RequestValidationException` em campo/direção inválidos.
- **`RetryUtils`** — `withRateLimitRetry(Supplier<T>)` retenta indefinidamente com backoff exponencial (2s a 60s) só em `429`, propagando qualquer outro erro na primeira tentativa.
- **`ThreadUtils`** — `sleepUninterruptibly(millis)`: dorme sem propagar `InterruptedException`, só restaura a interrupt flag.
- **`TmdbImageUtils`** — fábrica de URLs completas do CDN de imagens do TMDB (`posterSmall/Medium/Large`, `backdrop`, `logo`, `thumbnail`), null-safe, compartilhada pelos domínios `tmdb` e `anime`.
- **`VideoFileLocationUtils`** — `resolvePath(videoFile, storageProviderClientFactory, cacheDir)` resolve o caminho em disco de um `VideoFile`: direto se o provider for `LOCAL`, via cache local se for remoto e já tiver terminado de baixar.

## Dependências internas e externas
- Internas: `model.CacheEntry` (via `CacheUtils`), `exceptions.{RequestValidationException, IntegrationException, ResourceNotFoundException, IntegrationNotConfiguredException}`, e — só em `VideoFileLocationUtils` — tipos de domínio: `domain.cache.model.VideoFileCache`, `domain.storageprovider.provider.StorageProviderClientFactory`, `domain.videofile.model.VideoFile`, além dos DTOs gerados `api.dto.{CacheStatus, StorageProviderType}`.
- Externas: `io.quarkus.panache.common.Sort`, `io.quarkus.logging.Log`, `jakarta.ws.rs.WebApplicationException`.
- Dependências entre os próprios utils: `RetryUtils` usa `ThreadUtils.sleepUninterruptibly` para a espera entre tentativas.

## Módulos relacionados
Consumido por quase todo `domain/`: `anibridge`, `anilist`, `anime`, `cache`, `ingestion`, `mediafolder`, `mediaprobe`, `playback`, `storageprovider`, `tmdb`, `videofile` (o domínio `notification` não usa nenhum utilitário deste pacote hoje). Hoje `PaginationUtils.toSort` só é efetivamente chamado por `mediafolder`, apesar de ser desenhado como reutilizável por qualquer entidade paginada.

## Pontos de entrada
Todos os métodos são estáticos e chamados diretamente pelo código de domínio; não há scheduler, resource ou listener neste pacote.

## Fluxos importantes
- **Cache-first de integrações externas** (AniList, TMDB): `service` monta a chave com `CacheUtils.buildKey`, chama `CacheUtils.resolve` passando leitor/escritor de cache da própria entidade e o `loader` que chama o client externo.
- **Retry em rate limit**: `service` embrulha a chamada ao client externo em `IntegrationException` e passa pra `RetryUtils.withRateLimitRetry`, que só retenta se a causa for `WebApplicationException` com status `429`.
- **Resolução de arquivo para ffprobe/transcode**: `mediaprobe` e `playback` chamam `VideoFileLocationUtils.resolvePath` antes de operar sobre o arquivo físico, recebendo `ResourceNotFoundException` ou `IntegrationNotConfiguredException` já prontas quando o arquivo não está disponível.

## Arquivos críticos
- `CacheUtils.java` — ponto único do fluxo cache-first; qualquer integração externa nova com cache passa por aqui.
- `VideoFileLocationUtils.java` — único utilitário que depende de tipos de `domain/` (3 domínios: `cache`, `storageprovider`, `videofile`); mudança na regra de localização de arquivo afeta `mediaprobe` e `playback` ao mesmo tempo.
- `RetryUtils.java` + `ThreadUtils.java` — única estratégia de retry/backoff do projeto; qualquer integração sensível a rate limit depende deles.

## Observações técnicas e débitos identificados
- `PaginationUtils` e `VideoFileLocationUtils` lançam `AkariException` (subclasses de `exceptions/`) diretamente, fora de `domain/` — o próprio Javadoc de `VideoFileLocationUtils` registra isso como precedente intencional ("um helper de `utils/` pode lançar as exceções da aplicação diretamente quando a regra de erro é a mesma pra todo chamador"), não é acidental.
- `VideoFileLocationUtils` é o único arquivo do pacote que importa classes de `domain/` (`VideoFileCache`, `VideoFile`, `StorageProviderClientFactory`) em vez de só tipos primitivos/genéricos — ele próprio justifica isso no Javadoc como lógica compartilhada entre `mediaprobe` e `playback`, mas é uma exceção notável à ideia de "helper puro e geral, sem estado" que descreve o restante do pacote: ele conhece modelos concretos de 3 domínios.
