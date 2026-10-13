## Objetivo do módulo

Integrar o akari com a API v3 do TMDB (The Movie Database) para busca e enriquecimento de
metadados de filmes e séries: busca paginada por texto, detalhes completos, imagens, traduções e
episódios por temporada — tudo com cache-first para reduzir chamadas upstream e absorver
indisponibilidade/limites de taxa do TMDB.

## Responsabilidade principal

`TmdbService` concentra toda a regra de negócio do domínio: resolve preferências
(`SettingService`), monta chave de cache, decide cache-hit vs. upstream (`CacheUtils.resolve`),
chama `TmdbClient` e traduz falhas HTTP em exceções de negócio (`IntegrationException`,
`ResourceNotFoundException`). `TmdbSearchResource` é a camada burra que só expõe ao contrato HTTP
os dois endpoints de busca; `TmdbMapper` converte os itens crus de busca para os DTOs de resposta
da API, absolutizando URLs de imagem. `TmdbClient`/`TmdbAuthHeadersFactory` cuidam do transporte
cru e da autenticação.

Apesar do nome, `TmdbService` não é só "busca": é o único ponto de acesso ao TMDB usado por
todo o sistema (ver "Módulos relacionados"), cobrindo também detalhes, imagens, traduções e
temporadas — funcionalidade que não tem endpoint HTTP próprio neste domínio.

## Funcionalidades existentes

Expostas via HTTP (`TmdbSearchResource` → `TmdbSearchApi`):
- `GET /v1/tmdb/search/movies` — busca paginada de filmes (`searchTmdbMovie`).
- `GET /v1/tmdb/search/tv-shows` — busca paginada de séries (`searchTmdbTv`).

Não expostas via HTTP neste domínio, mas implementadas em `TmdbService` e usadas
internamente por outros domínios (chamada direta ao service, sem passar por `TmdbSearchResource`):
- `getMovieDetails` / `getTvDetails` — `GET /3/movie/{id}` e `GET /3/tv/{id}`.
- `getMovieImages` / `getTvImages` — `GET /3/movie|tv/{id}/images`.
- `getMovieTranslations` / `getTvTranslations` — `GET /3/movie|tv/{id}/translations`.
- `getSeason` — `GET /3/tv/{id}/season/{n}` (episódios de uma temporada).

Todas essas operações são cache-first (TTL via preferência `tmdb.cache.ttl`) e aceitam
`forceRefresh` para ignorar o cache.

## Dependências internas e externas

Internas (outros domínios usados por este):
- `domain.cache.model.IntegrationCache` — entidade de cache genérico usada por
  `TmdbService` via `CacheUtils.resolve`.
- `domain.setting.service.SettingService` — todas as preferências `tmdb.*`:
  `tmdb.api-key` (token), `tmdb.cache.ttl`, `tmdb.default-language`, `tmdb.include-adult`,
  `tmdb.image.logo-language`, `tmdb.image.backdrop-language`. Usado tanto em
  `TmdbService` quanto em `TmdbAuthHeadersFactory`.
- `utils.CacheUtils`, `utils.HttpErrorUtils`, `utils.TmdbImageUtils` — helpers compartilhados
  (cache-aside, classificação de erro HTTP 404, montagem de URL de CDN).
- `exceptions.*` (`AkariException` e subclasses) — `IntegrationException`,
  `IntegrationNotConfiguredException`, `ResourceNotFoundException`.

Externa:
- TMDB API v3 (`quarkus.rest-client.tmdb.url`, default `https://api.themoviedb.org`), via
  `TmdbClient` (MicroProfile REST Client), autenticado com `Authorization: Bearer <token>`
  injetado por `TmdbAuthHeadersFactory`.

## Módulos relacionados

`TmdbService` é consumido diretamente (chamada de service para service, sem passar pelo
`TmdbSearchResource`) por:
- `domain.anime.service.AnimeTmdbService` — usa `getTvDetails`/`getMovieDetails`,
  `getTvImages`/`getMovieImages`, `getTvTranslations`/`getMovieTranslations` para montar o
  schema de overrides (`TmdbOverridesSchema`) e pré-preencher poster/backdrop/logo do anime
  vinculado.
- `domain.anime.service.AnimeTmdbFilePairingService` — usa `getSeason` (via
  `TmdbSeasonSummaryResponse`/`TmdbSeasonResponse`) para montar o schema de pareamento de
  arquivo↔episódio.
- `domain.anime.mapper.AnimeTmdbFilePairingMapper` — converte `TmdbSeasonResponse`/
  `TmdbEpisodeResponse` para os DTOs de contrato do pareamento.
- `domain.anibridge.service.AnibridgeResolutionService` — usa `TmdbSeasonSummaryResponse`
  (dentro de `TmdbMappingCandidate`) para resolver o melhor candidato de binding anime↔TMDB.
- `domain.ingestion.service.FolderAutoBindService` — orquestra o binding automático de pasta
  (chama `AnimeTmdbService`/`AnimeTmdbFilePairingService`/`AnibridgeResolutionService`, e usa
  `TmdbEpisodeResponse` diretamente para resolver o episódio de um arquivo).

`utils.TmdbImageUtils` é explicitamente documentado como compartilhado entre `tmdb` e `anime`
para garantir os mesmos tamanhos de imagem nos dois domínios.

Nenhum outro domínio depende de `TmdbSearchResource`/`TmdbSearchApi` — o acoplamento cruzado é
todo via `TmdbService` e os DTOs de `client/dtos`.

## Pontos de entrada

- `TmdbSearchResource#searchTmdbMovie` / `#searchTmdbTv` — únicos pontos de entrada HTTP do
  domínio.
- `TmdbService` — ponto de entrada programático usado por outros domínios para detalhes,
  imagens, traduções e temporadas (não tem contrato HTTP próprio).

## Fluxos importantes

**Busca paginada (cache-first):** `TmdbSearchResource` → `TmdbService.searchMovies`/
`searchTv` → resolve `includeAdult` e `language` via `SettingService` → monta `cacheKey` com
`CacheUtils.buildKey` → `CacheUtils.resolve` lê `IntegrationCache` (fresco se dentro do TTL e
`forceRefresh` não é `true`); em cache miss, chama `TmdbClient.searchMovie`/`searchTv` com
`page + 1` (akari é 0-based, TMDB é 1-based), mapeia os itens via `TmdbMapper`
(`id`→`tmdbId`, `posterPath`→`posterUrl` em `w185`, `backdropPath`→`backdropUrl` em `original`)
e grava o resultado já montado (`TmdbMovieSearchResultPage`/`TmdbTvSearchResultPage`) de volta em
`IntegrationCache`.

**Autenticação dinâmica:** toda chamada de `TmdbClient` passa por `TmdbAuthHeadersFactory`
(`@RegisterClientHeaders`), que lê `tmdb.api-key` via `SettingService` a cada chamada — não dá
pra resolver em config estática porque o valor é uma preferência mutável em base, não uma env var
fixa. Preferência vazia/nula lança `IntegrationNotConfiguredException` (503).

**Tratamento de erro:** cada método `fetch*` de `TmdbService` captura
`ProcessingException`/`WebApplicationException` da chamada crua; se for 404
(`HttpErrorUtils.isNotFound`), lança `ResourceNotFoundException` (404); qualquer outra falha
vira `IntegrationException` (502), com log de erro antes.

**Enriquecimento cross-domain (fora deste domínio, mas disparado a partir dele):** quando
`domain.anime` ou `domain.ingestion` precisam da ficha completa (detalhes, imagens, traduções,
temporada/episódios) de um filme/série já vinculado a um anime, chamam `TmdbService`
diretamente — a lógica de cache-first e tradução de erro é reaproveitada integralmente; a
responsabilidade do domínio `anime` é só decidir *quando* buscar e *como* usar o resultado
(overrides, pareamento de arquivo).

## Arquivos críticos

- `service/TmdbService.java` — toda a regra de negócio (cache-first, paginação, tradução
  de erro); ponto único de acesso ao TMDB para o sistema inteiro.
- `client/TmdbAuthHeadersFactory.java` — autenticação dinâmica via preferência; único lugar que
  lança `IntegrationNotConfiguredException` neste domínio.
- `client/TmdbClient.java` — contrato de transporte cru com o TMDB; qualquer endpoint novo do
  TMDB consumido pelo akari entra aqui.
- `mapper/TmdbMapper.java` — único lugar que converte item de busca cru → DTO de contrato,
  incluindo a montagem de URL de imagem para os resultados de busca.
- `client/dtos/TmdbMovieDetailsResponse.java` / `TmdbTvDetailsResponse.java` — envelopes mais
  "largos" do domínio (todos os campos do detalhe TMDB), consumidos majoritariamente pelo
  domínio `anime`.

## Observações técnicas e débitos identificados

- **"SQLite interno" do contrato não corresponde à implementação.** O cache cache-first é a
  entidade JPA/Panache `IntegrationCache` (tabela `integration_cache`), persistida no PostgreSQL
  do próprio akari (`@Entity`, coluna `payload` tipada `JSON` via `@JdbcTypeCode(SqlTypes.JSON)`),
  não em um banco SQLite separado. É um cache genérico de key/value compartilhado com o domínio
  `anilist` (mesma entidade, chaves diferentes) — não há nada específico de SQLite no código.
- **A paginação não "busca várias páginas upstream e recorta a pedida".** O tamanho de página do
  akari (`PAGE_SIZE = 20`) é igual ao bloco fixo do TMDB (20), então a conversão é 1:1
  (`page + 1`, sem agregação de múltiplas páginas nem corte/slice). Não existe no código nenhuma
  lógica de buscar mais de uma página upstream por chamada — se o contrato descrever esse
  comportamento, ele não está implementado aqui.
- **Normalização camelCase é dupla, por camadas diferentes.** Os DTOs de `client/dtos` já
  convertem `snake_case`→camelCase na própria desserialização Jackson (via `@JsonProperty`), e é
  isso que o restante do sistema consome (incluindo `anime`/`anibridge`/`ingestion`). O
  `TmdbMapper` faz uma segunda conversão, mas só para os DTOs de busca expostos no contrato HTTP
  deste domínio (`TmdbMovieSearchResult`/`TmdbTvSearchResult`) — os demais DTOs de detalhe,
  imagem, tradução e temporada são usados "crus" (sem mapper) pelos consumidores cruzados.
  **Aceito** (confirmado pelo mantenedor): intencional porque esses DTOs nunca saem por uma API
  HTTP própria deste domínio, só circulam entre services.
- **Nenhum DTO de `client/dtos` está órfão.** Todos os ~22 records são usados, diretamente ou
  como campo aninhado de outro DTO do próprio pacote (confirmado por leitura de todos os
  arquivos). O "não usado" aparente de alguns (ex. `TmdbCreatedByResponse`,
  `TmdbCrewMemberResponse`, `TmdbGenreResponse`, `TmdbGuestStarResponse`, `TmdbNetworkResponse`,
  `TmdbProductionCompanyResponse`, `TmdbProductionCountryResponse`, `TmdbSpokenLanguageResponse`,
  `TmdbEpisodeSummaryResponse`) é porque eles só aparecem como campo de
  `TmdbMovieDetailsResponse`/`TmdbTvDetailsResponse`/`TmdbEpisodeResponse`, nunca importados
  soltos fora deste pacote.
- **Classe renomeada de `TmdbSearchService` para `TmdbService`** (confirmado pelo mantenedor): o
  nome antigo remetia só a "busca", mas a classe sempre foi o client interno de
  detalhes/imagens/traduções/temporada usado por `anime`/`anibridge`/`ingestion` — `TmdbService`
  descreve melhor o escopo real. O resource HTTP continua `TmdbSearchResource` (esse, sim, só
  cobre os dois endpoints de busca).
- **Acoplamento direto service→service entre domínios.** `anime`, `anibridge` e `ingestion`
  injetam `TmdbService` diretamente, sem passar por `TmdbSearchResource`/contrato HTTP —
  consistente com a convenção do projeto (sem camada comum/hexagonal), mas significa que
  qualquer mudança de assinatura em `TmdbService` tem raio de impacto em três domínios
  fora de `tmdb`.
- **Erros de rede/5xx fora de 404 não distinguem causa.** Todo erro não-404 vindo do TMDB (rate
  limit 429, 401 por token inválido, timeout) é tratado igual: vira `IntegrationException`
  genérica (502). Não há tratamento específico para 429 (rate limit) neste domínio — o retry
  para rate limit observado no fluxo de binding (`RetryUtils.withRateLimitRetry`) é feito pelos
  chamadores (`domain.anime`, `domain.ingestion`), não por `TmdbService`. **Backlog real**
  (confirmado pelo mantenedor, mesmo item do risco geral de retry ad hoc).
