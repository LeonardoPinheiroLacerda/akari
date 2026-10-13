# anibridge

## Objetivo do módulo

Manter, localmente e atualizado, um dataset de mapeamento episódio-a-episódio entre animes
(identificados por AniList ou MyAnimeList) e seus equivalentes no TMDB (filme ou temporada de
série). Esse dataset é publicado por terceiros (projeto externo `anibridge/anibridge-mappings`,
no GitHub) e serve pra resolver o problema de "ordem de exibição japonesa" divergir da "ordem de
catalogação TMDB" (temporadas fragmentadas, specials fora de ordem, numeração absoluta etc.).

**Confirmado pelo mantenedor:** o nome "anibridge" vem do projeto upstream que fornece o dataset —
o domínio no akari é consumidor desse dataset, não produtor.

## Responsabilidade principal

Baixar a release mais recente do dataset (GitHub Releases), parsear o formato próprio do
upstream pra um modelo relacional local (`MappingSegment`), e responder consultas desse mapeamento
pra quem precisar ligar um anime a um título/temporada do TMDB e converter números de episódio.
Resolve também ambiguidade quando o dataset aponta mais de um candidato TMDB pro mesmo anime.

## Funcionalidades existentes

- Ingestão periódica (`AnibridgeScheduler`, boot + a cada 1h) que compara a release mais recente
  do GitHub com a última ingerida (por `updated_at`) e, só se mudou, baixa e reprocessa o dataset
  inteiro (`AnibridgeService.refresh()`).
- Parsing do `mappings.min.json` (dict aninhado `origem -> destino -> rangeOrigem -> rangeDestino`)
  pra `MappingSegment` (`AnibridgeMappingParser`), ignorando entradas fora do shape esperado
  (multi-range com vírgula, providers desconhecidos).
- Consulta crua dos segments por `anilistId`, por `malId` ou pelo lado TMDB
  (`AnibridgeMappingService`).
- Resolução do "melhor candidato" TMDB pra um anime, com critério de desempate que penaliza
  candidatos suspeitos (sobreposição interna de range, ou cobertura maior que o total de episódios
  real do anime) antes de comparar cobertura (`AnibridgeResolutionService.resolveBestCandidate`).
- Conversão de episódio do anime pra temporada/episódio do TMDB, incluindo razão de conversão
  (`ratio`, para casos como 2 episódios de anime = 1 de TMDB) (`resolveEpisode`).
- Detecção de sobreposição entre segments de um mesmo candidato (`hasOverlap`) e resolução de
  conflito confirmando contra o TMDB quais temporadas do candidato ainda existem de fato
  (`resolveSafeSegments`), descartando segments residuais/órfãos do dataset upstream.
- Restrição de um conjunto de segments a uma única temporada isolada e completa
  (`restrictToFullSeasonCoverage`), usada por quem precisa confirmar que o candidato cobre do
  episódio 1 ao total sem emendar temporadas.

## Dependências internas e externas

**Internas (akari):**
- `domain.tmdb.service.TmdbService` e `domain.tmdb.client.dtos.TmdbSeasonSummaryResponse` —
  usados só por `AnibridgeResolutionService.resolveSafeSegments` pra confirmar quais temporadas
  ainda existem no TMDB antes de resolver um conflito de sobreposição.
- `api.dto.TmdbMediaType` (DTO gerado do contrato) — usado em `MappingSegment`,
  `TmdbMappingCandidate` e nos parsers, pra tipar o lado TMDB do mapeamento.
- `exceptions.IntegrationException` e `utils.RetryUtils` (utilitários transversais, fora de
  `domain/`).

**Externas:**
- GitHub REST API (`AnibridgeClient`, `GET /repos/anibridge/anibridge-mappings/releases/latest`),
  via `quarkus.rest-client.anibridge.url` (padrão `https://api.github.com`).
- Download direto do asset `mappings.min.json` via `java.net.http.HttpClient` puro (não passa pelo
  REST client MicroProfile) — a URL de download do GitHub redireciona pra uma CDN em outro host,
  o que o REST client não segue da mesma forma.

## Módulos relacionados

- **`domain.ingestion`** (`FolderAutoBindService`) — único consumidor real do anibridge. Usa
  `AnibridgeResolutionService` pra resolver o candidato TMDB de um anime recém-vinculado e pra
  parear episódio a episódio dos arquivos de uma pasta.
- **`domain.tmdb`** — dependência de saída do `AnibridgeResolutionService` (confirmação de
  temporadas existentes).
- **`domain.anilist`** — não é dependência direta de código, mas semanticamente adjacente: o
  `anilistId` é a chave primária usada nas consultas (`findByAnilistId`), e é `FolderAutoBindService`
  quem faz a ponte entre os dois domínios (busca na AniList, depois resolve no anibridge).

## Pontos de entrada

Nenhum endpoint HTTP — domínio puramente interno, não aparece em `openapi.yaml` nem tem `resource/`.
Os pontos de entrada são:
- `AnibridgeScheduler` — boot (`@Observes StartupEvent`) e `@Scheduled(every = "1h")`, aciona
  `AnibridgeService.refresh()`.
- `AnibridgeResolutionService` (API pública do domínio pra quem precisa resolver mapeamento) e,
  secundariamente, `AnibridgeMappingService` (busca crua) — injetados hoje só por
  `FolderAutoBindService` (domínio `ingestion`).

## Fluxos importantes

**Refresh do dataset** (`AnibridgeService.refresh()`):
1. Consulta a release mais recente no GitHub.
2. Compara `release.updatedAt()` com `MappingRelease.upstreamUpdatedAt` já persistido — se igual,
   aborta sem baixar nada.
3. Localiza o asset `mappings.min.json` na release, baixa via `HttpClient` pra um arquivo
   temporário, parseia (`AnibridgeMappingParser`), e substitui *todos* os `MappingSegment`
   (`deleteAll()` + `persist(segments)`).
4. Atualiza o singleton `MappingRelease` (tag, datas, contagem de segments).
5. Protegido por um `AtomicBoolean` simples — chamada concorrente (boot + scheduled) é ignorada,
   não enfileirada nem paralelizada.

**Resolução de candidato e pareamento** (orquestrado por `FolderAutoBindService`, fora deste
domínio):
1. `resolveBestCandidate(anilistId, malId, totalEpisodes)` — tenta por AniList, cai pra MAL se
   vazio; agrupa segments por `tmdbId:mediaType`; escolhe o não-suspeito com maior cobertura.
2. `resolveSafeSegments(candidate)` — se não há sobreposição, usa os segments crus; se há e é TV,
   confirma contra o TMDB quais temporadas existem e filtra; se ainda sobrar sobreposição, desiste
   (`Optional.empty()`).
3. `resolveEpisode(segments, episodeNumber)` — acha o segment cujo range cobre o episódio e
   converte aplicando o `ratio` (default 1).

## Arquivos críticos

- `service/AnibridgeService.java` — orquestra o refresh fim-a-fim (GitHub → download → parse →
  substituição transacional).
- `service/AnibridgeResolutionService.java` — toda a lógica de desambiguação/conversão; é o
  "cérebro" do domínio, muito mais denso que o resto.
- `parser/AnibridgeMappingParser.java` — único lugar que entende o formato textual do dataset
  upstream (`anilist:<id>`, `tmdb_show:<id>:s<n>`, ranges com `|ratio`); qualquer mudança de
  formato do dataset exige revisão aqui.
- `model/MappingSegment.java` — entidade central; todo refresh substitui a tabela inteira.
- `client/AnibridgeClient.java` — único ponto de acoplamento com a API do GitHub (não cobre o
  download do asset, feito fora do REST client).

## Observações técnicas e débitos identificados

- **Substituição total a cada refresh:** `MappingSegment.deleteAll()` seguido de
  `persist(segments)` roda dentro de uma única `@Transactional`, mas não há nenhuma janela de
  "versão anterior ainda consultável durante o refresh" — se o processo falhar entre o delete e o
  persist, a transação inteira rola back (comportamento correto), mas isso significa que qualquer
  consulta concorrente durante a janela de refresh vê a tabela momentaneamente vazia dentro da
  mesma transação de persistência (não é visível a outras transações até commit, mas é um ponto de
  atenção se o volume de segments crescer muito e o refresh passar a demorar). **Backlog real**
  (confirmado pelo mantenedor).
- **Download fora do REST client:** o asset em si é baixado com `HttpClient` puro, diferente do
  padrão do resto do projeto (REST client MicroProfile). Justificado no javadoc (redirecionamento
  pra CDN), mas é uma exceção notável ao padrão de `client/`.
- **Acoplamento com `domain.tmdb`:** `AnibridgeResolutionService` injeta `TmdbService`
  diretamente — é a única dependência de saída do domínio pra outro domínio de negócio (justificada:
  precisa confirmar existência de temporada no TMDB pra resolver conflito). Está de acordo com a
  convenção de "service pode injetar service de outro domínio quando a regra atravessa domínios".
- **Nenhum teste de unidade observado** nos arquivos lidos para a lógica de resolução de
  candidato/sobreposição (`AnibridgeResolutionService`), que é a parte mais complexa e com mais
  regras implícitas (critério de "suspeito", merge de ranges sobrepostos) — confirmado: não há
  diretório de testes no projeto (`src/test` não existe). **Backlog real** (confirmado pelo
  mantenedor).
- **Mal id como fallback silencioso:** `resolveBestCandidate` cai de AniList pra MAL sem nenhum
  sinal pra quem chama sobre qual fonte foi usada — aceitável dado que ambas resolvem pro mesmo
  `MappingSegment`, mas dificulta debug caso as duas fontes divirjam no dataset upstream.
