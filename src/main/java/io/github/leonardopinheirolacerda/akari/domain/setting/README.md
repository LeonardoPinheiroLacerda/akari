## Objetivo do módulo

Preferências administráveis do admin — par key/value seedado por migração, lido em runtime por
outros domínios. Tag `Setting` do contrato: expõe as preferências pro portal listar, pesquisar
por nome e atualizar; preferências `secret` (ex.: `tmdb.api-key`) nunca têm o `value` ecoado —
só o flag `configured`/mascaramento indica se há valor cadastrado. API `/v1/settings`.

## Responsabilidade principal

Expor leitura/busca/atualização das preferências pro portal administrar, e servir como ponto
único de leitura tipada (getters) para outros domínios configurarem seu comportamento (TMDB,
AniList, cache, playback).

## Funcionalidades existentes

- `GET /v1/settings?name=` — lista preferências, filtro opcional por substring
  case-insensitive na chave, ordenadas por chave.
- `PUT /v1/settings/{key}` — atualiza o `value` de uma preferência existente.
- Mascaramento: preferências com `secret = true` nunca ecoam o `value` em claro — é
  substituído por `"***"` tanto em `listSettings` quanto em `updateSetting`.
- Getters tipados usados por outros domínios: `getAnilistCacheTtl`, `getTmdbCacheTtl`,
  `getTmdbApiKey`, `isTmdbConfigured`, `getTmdbDefaultLanguage`, `getTmdbImageLogoLanguage`,
  `getTmdbImageBackdropLanguage`, `isTmdbIncludeAdult`, `getCacheMaxSizeBytes`, `getCacheDir`,
  `getPlaybackTranscodeHardwareAcceleration`, `getPlaybackSessionHeartbeatTtl`,
  `getPlaybackWorkBaseDir`.

## Dependências internas e externas

- Nenhuma dependência de outro domínio do projeto — confirmado por grep (`akari.domain.*`) nos
  4 arquivos do pacote: nenhum resultado fora de `setting`.
- `exceptions.ResourceNotFoundException` — lançada pelos getters tipados e por `updateSetting`
  se a chave não existir (linha nunca seedada por migração).
- `io.quarkus.hibernate.orm.panache.Panache` — `flush()` explícito em `updateSetting`.

## Módulos relacionados

- `tmdb` (`TmdbService`, `TmdbAuthHeadersFactory`) — api-key, idiomas, include-adult.
- `anilist` (`AnilistSearchService`) — cache TTL.
- `cache` (`CacheService`, `CacheQueueProcessorService`) — diretório e tamanho máximo do cache.
- `playback` (`PlaybackService`) — aceleração de hardware, heartbeat TTL, diretório de
  trabalho.
- `mediaprobe` (`MediaProbeService`) — lê `getCacheDir()` para resolver o cache local de vídeo.
- `ingestion` (`IngestionService`) — lê `isTmdbConfigured()` como precheck antes de etapas que
  dependem do TMDB.
- `anime` (`AnimeTmdbService`) — leitura de preferência TMDB.

## Pontos de entrada

- `SettingResource` (implementa `SettingApi` gerado) — camada HTTP (`listSettings`,
  `updateSetting`).
- `SettingService` — os getters tipados são injetados e chamados diretamente por pelo menos 8
  outros domínios, fora da camada HTTP; é o domínio mais amplamente consumido como dependência
  de configuração do projeto.

## Fluxos importantes

- `listSettings`: busca (`listAll` ou `findByName`) → mapeia para DTO → mascara `value` de cada
  item com `secret = true`.
- `updateSetting`: `findOrThrow(key)` → seta `value` → `Panache.flush()` explícito (necessário
  porque `@UpdateTimestamp` só atualiza `updatedAt` em memória durante o flush, que sem essa
  chamada só ocorreria no commit da transação — depois da resposta já ter sido montada com o
  valor antigo) → mapeia → mascara.
- Getters tipados: `findOrThrow(key).value` convertido pro tipo de destino (`Duration.parse`,
  `Long.parseLong`, `Boolean.parseBoolean`) ou devolvido como `String`; lançam
  `ResourceNotFoundException` só se a linha não existir no banco, não se `value` estiver
  nulo/em branco — preferências "ainda não configuradas" (ex. `cache.dir` antes do admin
  preencher) têm linha existente com `value` nulo, e cabe a quem chama o getter decidir se isso
  é um erro de negócio (citado no javadoc da classe como responsabilidade de quem chama, ex.
  uma eventual `IntegrationNotConfiguredException` fora deste domínio).

## Arquivos críticos

- `service/SettingService.java` — concentra os ~13 getters tipados que acoplam todo o resto do
  sistema a este domínio; `updateSetting` tem a lógica de flush documentada como necessária por
  efeito colateral do `@UpdateTimestamp`.

## Observações técnicas e débitos identificados

- Modelo chave/valor genérico sem tipagem no schema — toda conversão de tipo (`Duration`,
  `Long`, `Boolean`) acontece no ponto de leitura, sem validação de formato na escrita
  (`updateSetting` aceita qualquer string em `value`). Um valor mal formatado só falha em
  runtime no próximo getter tipado chamado, não na escrita via `PUT`. **Backlog real**
  (confirmado pelo mantenedor) — não é decisão definitiva, candidato a validação futura por
  tipo declarado.
- Nenhum endpoint gerencia o flag `secret` via API — **intencional** (confirmado pelo
  mantenedor): é decisão de segurança fixada por seed/migração, não uma lacuna de
  funcionalidade.
- `findOrThrow` lança `ResourceNotFoundException` se a chave nunca foi seedada — correto pelo
  javadoc, mas significa que adicionar um novo getter tipado em código sem a migração
  correspondente só quebra em runtime, no primeiro uso do getter, não no boot da aplicação.
  **Backlog real** (confirmado pelo mantenedor) — mesma categoria do item de validação acima.
