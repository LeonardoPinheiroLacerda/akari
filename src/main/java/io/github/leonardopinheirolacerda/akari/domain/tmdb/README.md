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
- `getSeason` — `GET /3/tv/{id}/season/{n}` (episódios de uma temporada), com nome e sinopse
  de cada episódio nos idiomas de `tmdb.episode.name-language` e
  `tmdb.episode.synopsis-language` (ver "Fluxos importantes").
- `getSeasonEpisodes` — só os episódios de uma temporada, sobre o `getSeason`; temporada
  inexistente no TMDB ou sem episódios vira lista vazia. Usado pelas estratégias de pairing de
  episódios, que andam pela série inteira.

Todas essas operações são cache-first (TTL via preferência `tmdb.cache.ttl`); todas menos
`getSeasonEpisodes` aceitam `forceRefresh` para ignorar o cache.

## Dependências internas e externas

Internas (outros domínios usados por este):
- `domain.cache.model.IntegrationCache` — entidade de cache genérico usada por
  `TmdbService` via `CacheUtils.resolve`.
- `domain.setting.service.SettingService` — as preferências `tmdb.api-key` (token),
  `tmdb.cache.ttl`, `tmdb.default-language` e `tmdb.include-adult`. Usado tanto em
  `TmdbService` quanto em `TmdbAuthHeadersFactory`. O `getSeason` também lê os idiomas dos
  episódios (`tmdb.episode.name-language`, `tmdb.episode.synopsis-language`) e o
  `tmdb.override.fallback-language`. As demais preferências de idioma dos overrides
  (`tmdb.image.logo-language`, `tmdb.override.title-language`/`synopsis-language`) são lidas
  pelo domínio `anime`, não por este.
- `utils.CacheUtils`, `utils.HttpErrorUtils`, `utils.RetryUtils`, `utils.TmdbImageUtils`,
  `utils.TmdbLanguageUtils` — helpers compartilhados (cache-aside, classificação de erro HTTP
  404, retry em rate limit, montagem de URL de CDN, ordem de procura dos idiomas).
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
  schema de overrides (`TmdbOverridesSchema`). `getTvDetails`/`getMovieDetails` também validam o `tmdbId` no bind e montam a
  ficha (`details`) devolvida em todo estado TMDB do anime (`TmdbAnimeStateResponse`).
- `domain.anime.service.AnimeTmdbFilePairingService` — usa `getSeason` (via
  `TmdbSeasonSummaryResponse`/`TmdbSeasonResponse`) para montar o schema de pareamento de
  arquivo↔episódio.
- `domain.anime.mapper.AnimeTmdbFilePairingMapper` — converte `TmdbSeasonResponse`/
  `TmdbEpisodeResponse` para os DTOs de contrato do pareamento.
- `domain.anibridge.service.AnibridgeResolutionService` — usa `TmdbSeasonSummaryResponse`
  (dentro de `TmdbMappingCandidate`) para resolver o melhor candidato de binding anime↔TMDB.
- `domain.anime.pairing.TmdbSeasonEpisodes` — usa `getTvDetails` e `getSeasonEpisodes` pra
  buscar as temporadas da série vinculada na resolução do pairing (proposta do portal e pairing
  automático da ingestão): o `TmdbEpisodeValidator` confirma o episódio resolvido e monta a
  metadata do pairing, e o `AirDateEpisodeMapper` lista os episódios exibidos no período do
  anime.
- `domain.anime.service.EpisodeContinuityResolutionService` — usa `getTvDetails` (temporadas
  regulares) e `getSeasonEpisodes` pra achar o episódio seguinte ao da âncora na proposta por
  continuidade.

`utils.TmdbImageUtils` é explicitamente documentado como compartilhado entre `tmdb` e `anime`
para garantir os mesmos tamanhos de imagem nos dois domínios.
`utils.TmdbLanguageUtils` também: os episódios (`getSeason`) e os overrides da série procuram os
idiomas na mesma ordem.

Nenhum outro domínio depende de `TmdbSearchResource`/`TmdbSearchApi` — o acoplamento cruzado é
todo via `TmdbService` e os DTOs de `client/dtos`.

## Pontos de entrada

- `TmdbSearchResource#searchTmdbMovie` / `#searchTmdbTv` — únicos pontos de entrada HTTP do
  domínio.
- `TmdbService` — ponto de entrada programático usado por outros domínios para detalhes,
  imagens, traduções e temporadas (não tem contrato HTTP próprio).

## Fluxos importantes

**Idioma dos episódios (`getSeason`)**: a temporada é buscada primeiro sem `language` (padrão do
TMDB, inglês), com a chave de cache `tv:season:{id}:{n}` — ela dá a estrutura e o texto de quem
não tem tradução. Com `tmdb.episode.name-language` e/ou `tmdb.episode.synopsis-language`
preenchidos, cada episódio procura o texto na ordem: idioma configurado (`pt-BR`), só o idioma
(`pt`, que o TMDB responde como `pt-PT`), `tmdb.override.fallback-language` e só o idioma dele —
mesma ordem dos overrides da série. A temporada em cada idioma é buscada só quando ainda falta
algum episódio, com cache próprio (`tv:season:{id}:{n}:{idioma}`). Episódio com sinopse vazia
num idioma conta como sem tradução nele — nesse caso o TMDB devolve nome genérico ("Episódio 5")
e sinopse vazia, e os dois saem do próximo idioma. Preferência vazia mantém o texto do padrão do
TMDB. Como as resoluções de pairing (anibridge e continuidade) e o schema do modal de pairing
passam todos pelo `getSeason`, o nome e a sinopse gravados no pairing seguem essas preferências —
inclusive nos pairings já gravados, que o step `TMDB_FILEPAIR` regrava em toda ingestão quando o
texto mudou (ver `domain/ingestion/README.md`).

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
`domain.anime` precisa da ficha completa (detalhes, imagens, traduções,
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

- **Cache-first é `IntegrationCache` em PostgreSQL** (tabela `integration_cache`, `@Entity`,
  coluna `payload` tipada `JSON` via `@JdbcTypeCode(SqlTypes.JSON)`) — cache genérico de key/value
  compartilhado com o domínio `anilist` (mesma entidade, chaves diferentes).
- **A paginação é `page + 1` direto** (0-based → 1-based): o tamanho de página do akari
  (`PAGE_SIZE = 20`) é igual ao bloco fixo do TMDB (20), então a conversão é 1:1, sem agregação de
  múltiplas páginas nem corte/slice.
- **Normalização camelCase é dupla, por camadas diferentes.** Os DTOs de `client/dtos` já
  convertem `snake_case`→camelCase na própria desserialização Jackson (via `@JsonProperty`), e é
  isso que o restante do sistema consome (incluindo `anime`/`anibridge`). O
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
- **`TmdbService` não é só "busca".** É o client interno de detalhes/imagens/traduções/temporada
  usado por `anime`/`anibridge`, não só os dois endpoints de busca HTTP. O resource
  HTTP continua `TmdbSearchResource` (esse, sim, cobre só a busca).
- **Acoplamento direto service→service entre domínios.** `anime` e `anibridge` injetam
  `TmdbService` diretamente, sem passar por `TmdbSearchResource`/contrato HTTP — consistente com
  a convenção do projeto (sem camada comum/hexagonal), mas significa que qualquer mudança de
  assinatura em `TmdbService` tem raio de impacto em dois domínios fora de `tmdb`.
- **Erros de rede/5xx fora de 404 não distinguem causa.** Todo erro não-404 vindo do TMDB (rate
  limit 429, 401 por token inválido, timeout) é tratado igual: vira `IntegrationException`
  genérica (502). O retry para rate limit (`RetryUtils.withRateLimitRetry`) é feito pelos
  chamadores (`domain.anime`, `domain.anibridge`) — `TmdbService` não tem
  retry; seus métodos falham direto num 429. **Backlog real**
  (confirmado pelo mantenedor, mesmo item do risco geral de retry ad hoc).
