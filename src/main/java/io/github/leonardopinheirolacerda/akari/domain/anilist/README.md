# anilist

## Objetivo do módulo

Encapsular toda a comunicação com a API GraphQL pública da AniList (`graphql.anilist.co`),
expondo tanto um endpoint público de busca paginada de anime quanto uma consulta por id usada
internamente por outros domínios, sempre com cache-first local pra evitar bater na AniList a cada
chamada.

## Responsabilidade principal

Traduzir a GraphQL da AniList (query `Page(page, perPage).media(search, type: ANIME)` e
`Media(id)`) pro vocabulário/DTOs do contrato akari, e gerenciar o cache dessas respostas via
`IntegrationCache` (domínio `cache`), com TTL configurável e opção de `forceRefresh`.

## Funcionalidades existentes

- **Busca paginada de anime** (`AnilistSearchService.search`) — exposta via
  `GET /v1/anilist/search` (tag `AnilistSearch` no contrato). Paginação 0-based no akari,
  convertida pra 1-based ao chamar a AniList (`page + 1`). Shape devolvido é o subset de `Media`
  sem `relations` (query própria, `AnilistQueries.MEDIA_SEARCH`, pra manter o payload enxuto).
- **Busca por id** (`AnilistSearchService.findById`) — **não exposta via API**, usada só
  internamente por outros domínios que precisam do shape completo, incluindo `relations`
  (`AnilistQueries.MEDIA_BY_ID`). Lança `ResourceNotFoundException` (404 traduzido) se a AniList
  não tiver o id.
- **Cache-first** em ambos os métodos, via `CacheUtils.resolve` + `IntegrationCache`
  (entidade Panache em **PostgreSQL**, coluna JSON, domínio `cache`) — TTL lido de
  `SettingService.getAnilistCacheTtl()` (chave de configuração `anilist.cache.ttl`).
  `forceRefresh=true` ignora o cache e força nova chamada upstream.
- **Mapeamento** `AnilistMediaResponse` → `AnilistMediaSummary` (DTO do contrato), incluindo
  conversão de `format`/`season` brutos (string livre da AniList) pros enums do contrato, com
  fallback pra `UNKNOWN` quando o valor não bate com nenhuma constante conhecida (`AnilistMapper`).

## Dependências internas e externas

**Internas (akari):**
- `domain.cache.model.IntegrationCache` — mecanismo de cache-aside genérico usado pelos dois
  métodos do service.
- `domain.setting.service.SettingService` — lê o TTL do cache (`anilist.cache.ttl`).
- `utils.CacheUtils`, `utils.HttpErrorUtils` e `exceptions.{IntegrationException,
  ResourceNotFoundException}` — utilitários/exceções transversais, fora de `domain/`.
- `api.dto.*` (DTOs gerados do contrato: `AnilistMediaSummary`, `AnilistMediaSummaryPage`,
  `AnilistFormat`, `AnilistSeason`) — bordas de entrada/saída do service e do mapper.

**Externas:**
- API GraphQL pública da AniList, via `quarkus.rest-client.anilist.url` (padrão
  `https://graphql.anilist.co`). Endpoint único (POST na raiz) — `AnilistClient` tem dois métodos
  (`search`, `findById`) que batem no mesmo endpoint com queries/variáveis diferentes.

## Módulos relacionados

- **`domain.anime`** (`AnimeService`, `AnimeRelationsService`, `AnimeMapper`) — maior consumidor
  interno do domínio. `AnimeService` usa `AnilistSearchService.findById` pra buscar metadata ao
  vincular (`bindAnimeToFolder`) e sincronizar (`syncAnime`) um anime; `AnimeMapper.applyAnilistData`
  aplica o `AnilistMediaResponse` cru no model `Anime`. `AnimeRelationsService` usa `findById`
  recursivamente pra seguir o grafo de relações (`relations.edges`) da AniList e montar
  `AnimeRelation`.
- **`domain.ingestion`** (`FolderAutoBindService`) — usa `AnilistSearchService.search` pra achar
  candidato de anime a partir do nome da pasta durante o auto-bind.
- **`domain.cache`** — infraestrutura de cache consumida pelo service (não o contrário).
- **`domain.setting`** — fonte do TTL de cache.

## Pontos de entrada

- **HTTP:** `AnilistSearchResource` implementa `AnilistSearchApi` (gerada do contrato) —
  único endpoint público do domínio, `GET /v1/anilist/search`. Camada burra, só delega pro
  service.
- **Interno:** `AnilistSearchService.findById(Integer anilistId, Boolean forceRefresh)` — não
  tem resource correspondente; é consumido diretamente por `domain.anime` e `domain.ingestion`
  (via `domain.anime`, indiretamente) como ponto de entrada de código, não de API.

## Fluxos importantes

**Busca paginada (`search`):**
1. Monta `cacheKey` a partir de `query + page + size`.
2. `CacheUtils.resolve` tenta ler `IntegrationCache` por essa chave (se `forceRefresh=false` e TTL
   não expirado); em cache miss, chama `fetchSearchFromAnilist`.
3. `fetchSearchFromAnilist` monta o `AnilistGraphqlRequest` com `page + 1` (conversão pra 1-based),
   chama `AnilistClient.search`, e traduz `AnilistPageResponse` pra `AnilistMediaSummaryPage`
   (contrato), incluindo `totalElements`/`totalPages` direto de `pageInfo`.
4. Resultado é persistido no cache antes de devolver.

**Busca por id (`findById`), consumida por `domain.anime`:**
1. Mesmo padrão cache-aside, chave `"media:" + anilistId`.
2. Em miss, chama `AnilistClient.findById`; erro HTTP 404 vira `ResourceNotFoundException`
   (traduzido via `HttpErrorUtils.isNotFound`); qualquer outro erro vira `IntegrationException`.
3. `AnimeRelationsService.syncRelations` consome esse método recursivamente (com `Set<Integer>
   visited` pra cortar ciclo) pra montar o grafo de relações (`SEQUEL`/`PREQUEL`/etc.) de uma
   franquia inteira.

## Arquivos críticos

- `service/AnilistSearchService.java` — concentra toda a regra de negócio do domínio (cache-first,
  tradução de erro, conversão de paginação); os dois métodos públicos (`search`, `findById`) são
  os únicos pontos de entrada de código do domínio.
- `client/AnilistQueries.java` — único lugar com o texto das duas queries GraphQL; qualquer campo
  novo precisado do contrato tem que ser adicionado aqui primeiro.
- `mapper/AnilistMapper.java` — ponto de tradução enum com fallback `UNKNOWN`; relevante porque a
  AniList pode introduzir valores novos de `format`/`season` sem aviso prévio.
- `client/dtos/AnilistMediaResponse.java` — shape bruto compartilhado pelas duas queries (campos
  de `relations` só preenchidos na busca por id).

## Observações técnicas e débitos identificados

- **Domínio com duas responsabilidades de exposição distintas sob o mesmo service:** `search` é
  público (API) e `findById` é só interno, mas ambos vivem em `AnilistSearchService` sem
  separação de pacote/anotação que marque essa diferença — a única forma de descobrir que
  `findById` não é exposto é checar `AnilistSearchResource` e o `openapi.yaml`. Não é uma violação
  da convenção descrita (resource pode simplesmente não cobrir todos os métodos do service), mas
  vale registrar como ponto de atenção pra quem for ler o domínio isoladamente.
- **`@Transactional` em métodos de leitura:** justificado no javadoc do service — o cache-aside
  escreve em `IntegrationCache` como efeito colateral de um cache miss, então a transação cobre a
  leitura (GraphQL) e a escrita no cache numa coisa só.
- **Acoplamento de `domain.anime` com o shape cru da AniList:** `AnimeMapper.applyAnilistData` e
  `AnimeRelationsService` consomem `AnilistMediaResponse`/`AnilistRelationEdgeResponse` (DTOs do
  `client/dtos` do anilist) diretamente, em vez de um DTO de domínio interno do anilist. Isso
  acopla `domain.anime` ao formato exato da resposta GraphQL da AniList — uma mudança de schema
  upstream se propaga até o mapper de `anime`. **Aceito** (confirmado pelo mantenedor): o shape
  da AniList é rico (campos como `relations`) e um DTO intermediário duplicaria praticamente
  o mesmo shape sem ganho real.
- **Nenhuma validação de rate limit própria neste domínio:** chamadas à AniList não passam por
  `RetryUtils` dentro do próprio `anilist` — quem usa `RetryUtils.withRateLimitRetry` em torno de
  `AnilistSearchService` é o chamador (`FolderAutoBindService`), não o service em si. Consistente
  internamente, mas significa que um consumidor que esqueça de envolver a chamada fica sem retry
  de rate limit. **Backlog real** (confirmado pelo mantenedor, mesmo item do risco geral de retry
  ad hoc documentado em `ARQUITETURA_DO_SISTEMA.md`).
