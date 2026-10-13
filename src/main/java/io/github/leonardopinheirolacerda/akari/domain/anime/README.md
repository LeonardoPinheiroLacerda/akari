# Domínio `anime`

## Objetivo do módulo

Manter a metadata de anime do akari: o vínculo entre uma pasta física do catalog (`mediafolder`) e a identidade canônica do título na AniList, mais uma camada opcional de enriquecimento via TMDB (imagens de melhor qualidade, traduções, overrides manuais e pairing de temporada/episódio por arquivo de vídeo). É o domínio mais central do projeto: concentra a identidade de cada anime que os demais domínios (ingestão automática, catalog, player) consultam.

## Responsabilidade principal

O domínio é organizado em dois eixos, refletidos nas tags do contrato (`Anime`, `AnimeRelations`, `AnimeTmdb`, `AnimeTmdbFilePairing`):

- **Eixo base (Anime):** binding anime↔pasta, fetch/sync na AniList, curadoria manual de raiz de franquia, status de revisão da ingestão, e o grafo de relações entre animes (sequel/prequel/side story).
- **Eixo TMDB (enriquecimento opcional):** binding TMDB↔anime, overrides manuais de título/sinopse/poster/backdrop/logo, e pairing por arquivo de vídeo (temporada/episódio/thumbnail). Um anime funciona sem TMDB; só banner e logo do `AnimeResponse` ficam `null` sem binding + overrides.

Cada faceta bem distinta tem seu próprio par resource/service, todos compartilhando o mesmo `model/` (principalmente `Anime`), conforme a convenção do projeto pra domínios com múltiplos resources:

| Resource | Service | Faceta |
|---|---|---|
| `AnimeResource` | `AnimeService` | binding↔pasta, sync AniList, curadoria, revisão |
| `AnimeRelationsResource` | `AnimeRelationsService` | grafo de relações, raízes de franquia |
| `AnimeTmdbResource` | `AnimeTmdbService` | binding TMDB, overrides, schema de overrides |
| `AnimeTmdbFilePairingResource` | `AnimeTmdbFilePairingService` | pairing TMDB por arquivo, schema de temporadas/episódios |

`AnimeOverrideService` **não tem resource próprio** — confirmado no código: é um `@ApplicationScoped` puro, sem endpoint HTTP, que resolve campo a campo qual valor prevalece (override manual do TMDB, quando setado, ou o dado cru da AniList). Existe pra não duplicar essa regra nos quatro services que montam `AnimeResponse`/`AnimeBindingSummary`/`AnimeRelationNode` a partir de `Anime`: `AnimeService` e `AnimeRelationsService` o injetam diretamente; `AnimeTmdbService`/`AnimeTmdbFilePairingService` não precisam dele porque não exibem o anime resolvido, só o estado de binding/pairing cru.

## Funcionalidades existentes

Eixo base:
- Vincular anime (por `anilistId`) a uma pasta do catalog, buscando os dados na AniList (`bindAnimeToFolder`).
- Remover a metadata de um anime, liberando a pasta (`deleteAnime`).
- Ler a metadata local por `anilistId` ou por `folderId` (`getAnime`, `getAnimeByFolder`).
- Ressincronizar a metadata com a AniList, preservando pasta e curadoria (`syncAnime`).
- Curadoria manual de raiz de franquia (`setAnimeFranchiseRoot`) e confirmação manual de revisão da ingestão (`markAsReviewed`), que trava o campo contra o auto-bind.
- Resumir o binding de várias pastas numa chamada (`getAnimeBindings`), consumido por `mediafolder` pra embutir `animeBinding` na listagem de pastas.

Grafo de relações:
- Sincronizar as arestas do grafo a partir da resposta da AniList, em cascata pros vizinhos já conhecidos localmente (`syncRelations`).
- Materializar o grafo via BFS bidirecional até profundidade configurável (`getAnimeRelationGraph`).
- Listar animes-raiz da coleção, paginados (`listRelationRoots`).
- Achar o prequel direto de um anime, usado pelo domínio `ingestion` pra calcular deslocamento de numeração absoluta (`findDirectPrequelId`).

Eixo TMDB:
- Vincular o anime a uma série/filme do TMDB, validando a existência do id e pré-preenchendo backdrop/logo com a imagem mais votada (melhor esforço) (`bindTmdb`).
- Remover o binding TMDB, limpando os overrides junto (`clearTmdbBinding`).
- Ler o estado TMDB atual (binding + overrides) (`getTmdbState`).
- Substituir o bloco de overrides inteiro (`updateTmdbOverrides`).
- Montar o schema de opções concretas por campo overridable: traduções de título/sinopse e galeria de imagens do TMDB (`getTmdbOverridesSchema`).

File pairing:
- Ler/criar/substituir/remover o pairing TMDB (temporada, episódio, thumbnail e metadata do episódio) de um arquivo de vídeo (`getTmdbFilePairing`, `updateTmdbFilePairing`, `deleteTmdbFilePairing`).
- Montar o schema de temporadas/episódios da série TMDB vinculada, pro cliente não adivinhar números (`getTmdbFilePairingSchema`).
- Resumir pairings de vários arquivos numa chamada (`getTmdbFilePairings`), consumido por `videofile` pra embutir `tmdbPairing` na listagem de arquivos.

## Dependências internas e externas

**Internas (entre classes do próprio domínio):**
- `AnimeService` injeta `AnimeRelationsService` (ressincroniza o grafo após bind/sync) e `AnimeOverrideService` (resolve campos exibidos).
- `AnimeRelationsService` injeta `AnimeOverrideService` (resolve título/thumbnail dos nós do grafo).
- `AnimeTmdbService` injeta `AnimeService` (busca o anime base, reusa `findOrThrow`).
- `AnimeTmdbFilePairingService` injeta `AnimeTmdbService` (reusa `findBoundAnimeOrThrow`).

**Externas (outros domínios), por service:**
- `AnimeService`: `anilist.service.AnilistSearchService` (fetch/sync), `mediafolder.service.MediaFolderService` (resolve a pasta no bind).
- `AnimeRelationsService`: `anilist.service.AnilistSearchService` (busca vizinhos durante o sync em cascata).
- `AnimeTmdbService`: `tmdb.service.TmdbService` (detalhes, imagens, traduções), `setting.service.SettingService` (idioma preferido de backdrop/logo).
- `AnimeTmdbFilePairingService`: `tmdb.service.TmdbService` (temporadas/episódios), `videofile.service.VideoFileService` (valida o arquivo).
- Model `Anime` referencia `mediafolder.model.MediaFolder` via `@OneToOne`.

Nenhum service do domínio injeta mais de dois domínios externos ao mesmo tempo — o acoplamento alto do domínio como um todo (5 domínios externos: `anilist`, `tmdb`, `mediafolder`, `videofile`, `setting`) é distribuído entre as quatro facetas, não concentrado num único service.

## Módulos relacionados

- **`ingestion`** é o maior consumidor externo: `FolderAutoBindService` orquestra as três etapas (bind AniList → bind TMDB → pairing de episódios) automaticamente durante a varredura de pastas, usando `AnimeService`, `AnimeTmdbService` e `AnimeTmdbFilePairingService` diretamente; `AbsoluteEpisodeOffsetService` usa `AnimeService.find` e `AnimeRelationsService.findDirectPrequelId` pra andar a cadeia de prequels; `FolderRemovalService` usa `AnimeService` pra resolver os `anilistId` antes de remover uma pasta.
- **`mediafolder`**: `MediaFolderService.listFolders` embute `AnimeBindingSummary` via `AnimeService.getAnimeBindings`; `Anime.folder` referencia `MediaFolder`.
- **`videofile`**: `VideoFileService.listVideoFiles` embute `TmdbFilePairingResponse` via `AnimeTmdbFilePairingService.getTmdbFilePairings`.
- **`anilist`** e **`tmdb`**: fontes de dados externos consumidas via seus `*SearchService`.
- **`setting`**: fornece a preferência de idioma usada no pré-preenchimento de imagens TMDB.

## Pontos de entrada

HTTP, via os quatro `*Resource` (`/v1/metadata/animes/...`, `/v1/metadata/folders/{folderId}/anime`, `/v1/metadata/anime-relations`, `/v1/metadata/files/{fileId}/tmdb-pairing`) — ver `openapi.yaml` linhas 856-1172, tags `Anime`, `AnimeRelations`, `AnimeTmdb`, `AnimeTmdbFilePairing`.

Programático, pra outros domínios (sem HTTP):
- `AnimeService.findByFolder`, `.exists`, `.find`, `.findOrThrow`, `.updateReviewStatusIfNotReviewed`, `.getAnimeBindings`.
- `AnimeRelationsService.findDirectPrequelId`.
- `AnimeTmdbFilePairingService.getTmdbFilePairings`.
- `AnimeTmdbService.findBoundAnimeOrThrow` (uso intra-domínio, mas público).

## Fluxos importantes

**Bind inicial (`AnimeService.bindAnimeToFolder`):** valida que não existe metadata pro anime nem binding prévio da pasta → busca na AniList → persiste `Anime` com `franchiseRootCuration = AUTO` → chama `AnimeRelationsService.syncRelations` pra popular o grafo.

**Sync de relações em cascata (`AnimeRelationsService.syncRelations`):** apaga as arestas que saem do anime e as recria a partir da resposta da AniList; pra cada relação que aponta pra um anime *já conhecido localmente*, salva a aresta e dispara recursivamente o mesmo sync pro vizinho (se ainda não visitado nessa chamada), buscando-o de novo na AniList. Relações pra animes não vinculados localmente são ignoradas — o grafo se fecha quando esses animes forem vinculados depois. Tudo dentro de uma única `@Transactional`.

**Grafo de relações (`AnimeRelationsService.getAnimeRelationGraph`):** BFS bidirecional sobre `AnimeRelation` (segue tanto `fromAnilistId` quanto `toAnilistId`) até `depth` saltos, classificando cada nó como raiz manual/natural/não-raiz via `RelationType.ANCESTOR_LINKS` (`PREQUEL`, `PARENT`).

**Resolução de overrides (`AnimeOverrideService`):** usada por `AnimeService.toResponse`/`toBindingSummary` e por `AnimeRelationsService` pra decidir, campo a campo, entre o override manual (TMDB) e o dado cru (AniList); banner e logo só existem via override (a AniList não tem esses campos).

**Bind TMDB (`AnimeTmdbService.bindTmdb`):** valida que o anime existe e ainda não tem binding → confirma o `tmdbId` no TMDB pro `mediaType` informado → grava o binding → `prefillImageOverrides` tenta preencher backdrop/logo com a imagem mais votada no idioma preferido (`SettingService`), com `RetryUtils.withRateLimitRetry`; falha aqui é engolida (log + segue sem imagem), nunca aborta o bind.

**Orquestração automática (`ingestion.FolderAutoBindService`):** varre uma pasta recém-detectada e encadeia bind AniList → bind TMDB → pairing de episódios, usando `AnimeService.updateReviewStatusIfNotReviewed` pra marcar o resultado (`PARTIAL`/`PENDING_REVIEW`) sem sobrescrever uma revisão manual já confirmada.

## Arquivos críticos

- `model/Anime.java` — entidade central; PK é o próprio `anilistId` (sem id surrogate), concentra dados AniList + binding/overrides TMDB + curadoria + revisão.
- `service/AnimeOverrideService.java` — motor único de resolução override↔dado cru, reaproveitado pelas outras facetas.
- `service/AnimeService.java` — binding↔pasta, sync, curadoria; ponto de entrada mais usado por outros domínios.
- `service/AnimeRelationsService.java` — sync recursivo do grafo e BFS de materialização; lógica mais complexa do domínio.
- `service/AnimeTmdbService.java` — binding TMDB, overrides, schema de opções; maior arquivo do domínio (344 linhas).
- `service/AnimeTmdbFilePairingService.java` — pairing por arquivo e schema de temporadas/episódios.
- `model/AnimeRelation.java` + `model/RelationType.java` — grafo de relações; `RelationType.ANCESTOR_LINKS` é a fonte única da regra "o que quebra uma raiz natural de franquia".

## Observações técnicas e débitos identificados

- **Uso inconsistente de `RetryUtils`:** só `AnimeTmdbService.prefillImageOverrides` usa `RetryUtils.withRateLimitRetry`. Todas as outras chamadas de rede do domínio — `anilistSearchService.findById` (bind, sync, cascata de `syncRelations`), `tmdbService.getTvDetails/getMovieDetails` (bind, schemas), `tmdbService.getSeason` (file-pairing schema) — não têm retry nem tratamento de rate limit. Sob rate limiting da AniList/TMDB, esses caminhos falham direto. **Backlog real** (confirmado pelo mantenedor, mesmo item do risco geral de retry ad hoc).
- **`syncRelations` roda recursão não limitada dentro de uma única `@Transactional`:** cada vizinho ainda não visitado dispara uma nova chamada de rede à AniList antes de continuar a recursão. Em franquias grandes, isso significa N chamadas de rede sequenciais dentro de uma transação de banco só, sem timeout ou circuit breaker visível — risco de transação longa/timeout proporcional ao tamanho da franquia. **Backlog real** (confirmado pelo mantenedor).
- **`getTmdbFilePairingSchema` faz uma chamada por temporada** (`tmdbService.getSeason`), sequencial, um N+1 clássico sobre a API do TMDB — **confirmado**: `getSeason` é cache-first via `IntegrationCache` (mesmo padrão dos demais métodos de `TmdbService`), então chamadas repetidas pela mesma temporada não batem de novo no upstream; o N+1 em si (N chamadas sequenciais na primeira vez) continua existindo, mas o custo de rede real só ocorre uma vez por temporada por TTL.
- **`clearTmdbBinding` não toca em `TmdbFilePairing`:** ao remover o binding TMDB, os overrides do anime são zerados, mas os pairings de episódio dos vídeos da pasta (nome, sinopse, thumbnail vindos do TMDB) permanecem intactos, agora desvinculados de qualquer binding TMDB ativo. **Lacuna a cobrir** (confirmado pelo mantenedor): o comportamento correto é limpar/invalidar os pairings junto ao remover o binding; vai pro backlog.
- **Sem paginação em lookups batch:** `AnimeService.getAnimeBindings` e `AnimeTmdbFilePairingService.getTmdbFilePairings` recebem a lista inteira de ids do chamador (`mediafolder`/`videofile`) sem limite — hoje acompanha o tamanho da página do chamador, mas não há um teto próprio caso isso mude. **Backlog real** (confirmado pelo mantenedor).
- Nenhum `TODO`/`FIXME`/`XXX` encontrado no domínio.
- Tratamento de concorrência no bind TMDB/AniList: não há lock explícito nem verificação de condição de corrida entre a checagem de duplicidade (`Anime.find`/`anime.tmdbId != null`) e o `persist()`/commit — duas requisições simultâneas pro mesmo `anilistId` podem ambas passar a validação antes de uma persistir (mitigado só pela constraint de PK no banco, que faria a segunda falhar com exceção não tratada especificamente aqui). **Backlog real** (confirmado pelo mantenedor, mesmo item do risco geral de corrida no bind).
- Boa prática observada: leituras usadas fora do contexto de request HTTP (`findByFolder`, `exists`, `find` em `AnimeService`) foram corretamente marcadas `@Transactional` mesmo sendo apenas leitura, já que `ingestion` as chama fora de uma transação ambiente — mostra atenção deliberada a esse detalhe, não um padrão acidental.
