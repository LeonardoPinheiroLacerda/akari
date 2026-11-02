# Domínio `anime`

## Objetivo do módulo

Manter a metadata de anime do akari: o vínculo entre uma pasta física do catalog (`mediafolder`) e a identidade canônica do título na AniList, mais uma camada opcional de enriquecimento via TMDB (imagens de melhor qualidade, traduções, overrides manuais e pairing de temporada/episódio por arquivo de vídeo). É o domínio mais central do projeto: concentra a identidade de cada anime que os demais domínios (ingestão automática, catalog, player) consultam.

## Responsabilidade principal

O domínio é organizado em dois eixos mais uma faceta de listagem que combina os dois, refletidos nas tags do contrato (`Anime`, `AnimeRelations`, `AnimeTmdb`, `AnimeTmdbFilePairing`, `AnimeEpisodes`):

- **Eixo base (Anime):** binding anime↔pasta, fetch/sync na AniList, curadoria manual de raiz de franquia, status de revisão da ingestão, o grafo de relações entre animes (sequel/prequel/side story) e a franquia organizada em raiz/temporadas/filmes/especiais pra tela de detalhe do app.
- **Eixo TMDB (enriquecimento opcional):** binding TMDB↔anime, overrides manuais de título/sinopse/poster/backdrop/logo, e pairing por arquivo de vídeo (temporada/episódio/thumbnail). Um anime funciona sem TMDB; só banner e logo do `AnimeResponse` ficam `null` sem binding + overrides.
- **Episódios (AnimeEpisodes):** resolve a pasta do anime (eixo base) e delega a listagem em si pro domínio `videofile`, já em ordem natural de nome — pronta pra tela de episódios do app, sem o cliente precisar descobrir `folderId` nem reordenar. O pareamento TMDB (eixo TMDB) só enriquece cada item (via `videofile`), não define mais a posição.

Cada faceta bem distinta tem seu próprio par resource/service, todos compartilhando o mesmo `model/` (principalmente `Anime`), conforme a convenção do projeto pra domínios com múltiplos resources:

| Resource | Service | Faceta |
|---|---|---|
| `AnimeResource` | `AnimeService` | binding↔pasta, sync AniList, curadoria, revisão |
| `AnimeRelationsResource` | `AnimeRelationsService` | grafo de relações, raízes de franquia, franquia organizada em raiz/temporadas/filmes/especiais |
| `AnimeTmdbResource` | `AnimeTmdbService` | binding TMDB, overrides, schema de overrides |
| `AnimeTmdbFilePairingResource` | `AnimeTmdbFilePairingService` | pairing TMDB por arquivo, schema de temporadas/episódios |
| `AnimeTmdbProposalResource` | `AnimeTmdbProposalService` | propostas de binding TMDB (anibridge) e de pairing de episódios (anibridge ou continuidade), só consulta |
| `AnimeEpisodeResource` | `AnimeEpisodeService` | listagem paginada de episódios (arquivos de vídeo) do anime, em ordem natural de nome |

`EpisodePairingResolutionService` e `AbsoluteEpisodeOffsetService` também não têm resource próprio: são a resolução do pairing TMDB dos arquivos de uma pasta pela estratégia do anibridge, compartilhada entre o pairing automático da ingestão (`FolderAutoBindService`, que grava e notifica) e a proposta de pairing do portal (`AnimeTmdbProposalService`, que só devolve) — por isso moram aqui, junto do pairing, e não em `ingestion` (que já depende de `anime`; o contrário faria um ciclo de services). `EpisodeContinuityResolutionService` (estratégia de continuidade, só usada pela proposta) e `EpisodePairingComparisonService` (comparação da resolução com os pares gravados, usada pela proposta e pelo pairing automático da ingestão — que grava só o que a proposta pré-selecionaria) também não têm resource.

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
- Montar a franquia de um anime já organizada em raiz/temporadas/filmes/especiais pra tela de detalhe do app (`getAnimeFranchise`).
- Achar o prequel direto de um anime, usado pelo `AbsoluteEpisodeOffsetService` pra calcular o deslocamento de numeração absoluta no pairing de episódios (`findDirectPrequelId`).

Eixo TMDB:
- Vincular o anime a uma série/filme do TMDB, validando a existência do id e pré-preenchendo backdrop/logo com a imagem mais votada (melhor esforço) (`bindTmdb`).
- Remover o binding TMDB, limpando junto os overrides e os pairings TMDB de todos os arquivos da pasta (`clearTmdbBinding`, via `TmdbFilePairing.deleteByFolderId`) — os pairings apontam pra temporadas/episódios do título desvinculado. Um novo bind volta a pré-preencher backdrop/logo; os pairings precisam ser refeitos.
- Ler o estado TMDB atual (binding + overrides) (`getTmdbState`). O estado devolvido por `getTmdbState`, `bindTmdb` e `updateTmdbOverrides` traz sempre `details`, a ficha resumida da série/filme vinculado como está no TMDB (título, título original, data, temporadas/episódios, pôster e backdrop principais, sem overrides), pra conferir se o binding aponta pro título certo. A ficha é lida cache-first, sem `language` (textos no idioma padrão do TMDB, `en-US`); se o TMDB falhar, a operação falha com o erro do TMDB (`502`/`503`) — no `updateTmdbOverrides`, desfazendo a gravação dos overrides.
- Substituir o bloco de overrides inteiro (`updateTmdbOverrides`).
- Montar o schema de opções concretas por campo overridable: traduções de título/sinopse e galeria de imagens do TMDB (`getTmdbOverridesSchema`).

Proposta de binding TMDB:
- Propor a série/filme do TMDB pra um `anilistId` (`POST /v1/metadata/tmdb/binding-proposals`, `AnimeTmdbProposalService.proposeTmdbBinding`), sem gravar nada: o melhor candidato do anibridge (`AnibridgeResolutionService.resolveBestCandidate`, o mesmo critério do bind automático da ingestão), com o `malId` (fallback do anibridge) e o total de episódios (desempate entre candidatos) lidos da AniList (`AnilistSearchService.findById`, cache-first). Por isso funciona mesmo com o anime ainda não salvo no akari. A resposta traz a mesma ficha `details` do estado TMDB (`AnimeTmdbService.fetchDetails`), que também confirma que o `tmdbId` existe. `404` se o anime não existir na AniList, se o anibridge não tiver mapeamento pra ele ou se o `tmdbId` não existir mais no TMDB. Pra efetivar, o portal chama o `bindTmdb` com o `tmdbId`/`mediaType` propostos.
- Propor o pairing TMDB de cada arquivo da pasta de um anime já vinculado a uma série do TMDB (`POST /v1/metadata/tmdb/file-pairing-proposals`, `AnimeTmdbProposalService.proposeTmdbFilePairing`), sem gravar nada, pela `strategy` pedida: `ANIBRIDGE` (padrão, a mesma resolução do pairing automático — `EpisodePairingResolutionService`) ou `CONTINUITY` (`EpisodeContinuityResolutionService`, ver abaixo). Devolve todos os arquivos da pasta na ordem natural dos nomes, com o `status` de cada um (`PAIRED` ou o motivo de não ter proposta; quais status pertencem a qual estratégia está no contrato).
- Sobre o resultado das duas estratégias, o `EpisodePairingComparisonService` compara com o que já está gravado, sem saber qual estratégia gerou a resolução: par atual do arquivo (`currentSeasonNumber`/`currentEpisodeNumber`), `change` (`NEW` sem par gravado, `SAME` igual ao gravado, `REPLACE` diferente), `usedByFileId` (outro arquivo que já usa o episódio proposto — pelo par gravado, ou por ter recebido a mesma proposta antes na ordem natural) e `selectedByDefault` (só `NEW` sem uso por outro arquivo, em qualquer temporada). Os pares gravados da pasta são lidos uma vez (`EpisodePairingComparisonService.findCurrentEpisodes`) e servem tanto à comparação quanto às âncoras da continuidade. `404` se o anime não existir ou não tiver binding TMDB; `400` se o binding for de filme; `502`/`503` se a estrutura da série não puder ser lida do TMDB na continuidade.

Resolução do pairing TMDB (`EpisodePairingResolutionService`, sem endpoint próprio):
- Número do episódio extraído do nome do arquivo (`ingestion.extractor.EpisodeNumberExtractor`), convertido pra temporada/episódio do TMDB pelo anibridge — só pelos segments que apontam pra série vinculada (`AnibridgeResolutionService.resolveCandidateFor`), pra nunca aplicar a numeração de um título sobre outro: sem nenhum, todos os arquivos saem `NO_MAPPING`. Segments conflitantes (`resolveSafeSegments` vazio) → `CONFLICTING_MAPPING`.
- Fallbacks: numeração absoluta, com offsets candidatos calculados pela cadeia de prequels da AniList (`AbsoluteEpisodeOffsetService`, tentados do mais próximo ao mais distante, só quando o mapeamento cobre a temporada isolada com segurança — senão `AMBIGUOUS_ABSOLUTE_NUMBERING`); e pasta de 1 arquivo com anime de 1 episódio → episódio 1.
- Cada episódio resolvido é confirmado no TMDB (`TmdbService.getSeasonEpisodes`, com retry de rate limit, temporada cacheada por pasta), de onde sai a metadata do pairing; se não existir (temporada inexistente, episódio fora dela ou falha na busca) → `NOT_FOUND_IN_TMDB`, sem pairing.

Resolução por continuidade (`EpisodeContinuityResolutionService`, sem endpoint próprio, só na proposta):
- Não consulta o anibridge: depende só do binding TMDB, da estrutura da série no TMDB (`TmdbService.getTvDetails`/`getSeasonEpisodes`, cache-first) e dos pairings já gravados na pasta — funciona justamente quando o anibridge não mapeia o anime (ex.: especiais pareados à mão na temporada 0).
- Os arquivos de um anime são os arquivos diretos da pasta dele, então estão todos no mesmo diretório: a continuidade anda pela pasta inteira na ordem natural dos nomes (`VideoFileService.listByFolderOrderedByNaturalSortKey`).
- Arquivo com par gravado é âncora e sai `PAIRED` com o próprio par. Cada arquivo sem par recebe o episódio seguinte ao da âncora e vira a âncora do próximo. Se esse episódio já está gravado em outro arquivo, ou já foi proposto antes nesta passada, a própria continuidade marca `EPISODE_IN_USE` (o `usedByFileId` vem da comparação) — e o arquivo continua sendo âncora, pra sequência não quebrar. Sem âncora antes → `NO_ANCHOR`; sem episódio seguinte → `END_OF_SERIES`.
- Depois do último episódio de uma temporada, segue no primeiro da próxima temporada com episódios, pelo número. A temporada 0 (especiais) não tem tratamento próprio: âncora nela continua dentro dela e, no fim, segue pra temporada 1; como vem antes de todas, nunca é a próxima de outra temporada. Temporada que não existe mais no TMDB (par gravado desatualizado) conta como vazia; outras falhas do TMDB propagam (`502`/`503`).

File pairing:
- Ler/criar/substituir/remover o pairing TMDB (temporada, episódio, thumbnail e metadata do episódio) de um arquivo de vídeo (`getTmdbFilePairing`, `updateTmdbFilePairing`, `deleteTmdbFilePairing`).
- Montar o schema de temporadas/episódios da série TMDB vinculada, pro cliente não adivinhar números (`getTmdbFilePairingSchema`).
- Resumir pairings de vários arquivos numa chamada (`getTmdbFilePairings`), consumido por `videofile` pra embutir `tmdbPairing` na listagem de arquivos.

Episódios:
- Paginar os episódios (arquivos de vídeo) do anime em ordem natural de nome (trechos numéricos comparam como número, `id` como desempate), delegando a listagem em si pro `videofile`; o pareamento TMDB, quando houver, só enriquece o item, não define a posição (`listAnimeEpisodes`).

## Dependências internas e externas

**Internas (entre classes do próprio domínio):**
- `AnimeService` injeta `AnimeRelationsService` (ressincroniza o grafo após bind/sync) e `AnimeOverrideService` (resolve campos exibidos).
- `AnimeRelationsService` injeta `AnimeOverrideService` (resolve título/thumbnail dos nós do grafo).
- `AnimeTmdbService` injeta `AnimeService` (busca o anime base, reusa `findOrThrow`).
- `AnimeTmdbFilePairingService` injeta `AnimeTmdbService` (reusa `findTvBoundAnimeOrThrow`).
- `AnimeTmdbProposalService` injeta `AnimeTmdbService` (reusa `fetchDetails` pra montar a ficha e `findTvBoundAnimeOrThrow`), `EpisodePairingResolutionService` (estratégia anibridge), `EpisodeContinuityResolutionService` (estratégia de continuidade) e `EpisodePairingComparisonService` (pares gravados e comparação) — só escolhe a estratégia e encadeia os três.
- `AnimeTmdbFilePairingMapper.toPairingRequest` é o único ponto que traduz um episódio do TMDB pros campos do pairing — usado por `EpisodePairingResolutionService` e `EpisodeContinuityResolutionService`.
- `EpisodePairingResolutionService` injeta `AbsoluteEpisodeOffsetService` (offsets de numeração absoluta).
- `AbsoluteEpisodeOffsetService` injeta `AnimeService` e `AnimeRelationsService` (anda a cadeia de prequels).
- `AnimeEpisodeService` injeta `AnimeService` (resolve o anime e a pasta).

**Externas (outros domínios), por service:**
- `AnimeService`: `anilist.service.AnilistSearchService` (fetch/sync), `mediafolder.service.MediaFolderService` (resolve a pasta no bind).
- `AnimeRelationsService`: `anilist.service.AnilistSearchService` (busca vizinhos durante o sync em cascata).
- `AnimeTmdbService`: `tmdb.service.TmdbService` (detalhes, imagens, traduções), `setting.service.SettingService` (idioma preferido de backdrop/logo).
- `AnimeTmdbFilePairingService`: `tmdb.service.TmdbService` (temporadas/episódios), `videofile.service.VideoFileService` (valida o arquivo).
- `AnimeTmdbProposalService`: `anilist.service.AnilistSearchService` (`malId` e total de episódios), `anibridge.service.AnibridgeResolutionService` (melhor candidato TMDB).
- `EpisodePairingResolutionService`: `anibridge.service.AnibridgeResolutionService` (candidato da série vinculada, segments, conversão de episódio), `videofile.service.VideoFileService` (arquivos da pasta), `tmdb.service.TmdbService` (temporadas/episódios), e a classe utilitária `ingestion.extractor.EpisodeNumberExtractor` (número do episódio no nome do arquivo).
- `EpisodeContinuityResolutionService`: `videofile.service.VideoFileService` (arquivos da pasta em ordem natural), `tmdb.service.TmdbService` (ficha e temporadas da série).
- `AnimeEpisodeService`: `videofile.service.VideoFileService` (`listVideoFilesOrderedByNaturalSortKey` — listagem em si, já com `tmdbPairing` embutido).
- Model `Anime` referencia `mediafolder.model.MediaFolder` via `@OneToOne`.

O acoplamento alto do domínio como um todo (7 domínios externos: `anilist`, `tmdb`, `anibridge`, `mediafolder`, `videofile`, `setting` e `ingestion`, este só pelo `EpisodeNumberExtractor`) é distribuído entre as facetas; o service mais acoplado é o `EpisodePairingResolutionService` (`anibridge`, `videofile`, `tmdb` e o extractor do `ingestion`), por concentrar a resolução do pairing.

## Módulos relacionados

- **`ingestion`** é o maior consumidor externo: `FolderAutoBindService` orquestra as três etapas (bind AniList → bind TMDB → pairing de episódios) automaticamente durante a varredura de pastas, usando `AnimeService`, `AnimeTmdbService`, `EpisodePairingResolutionService` (resolve o pairing), `EpisodePairingComparisonService` (compara com os pares gravados — só grava arquivo sem par, com episódio livre) e `AnimeTmdbFilePairingService` (grava) diretamente; `FolderRemovalService` usa `AnimeService` pra resolver os `anilistId` antes de remover uma pasta. No sentido contrário, `anime` usa de `ingestion` só o `extractor.EpisodeNumberExtractor` (no `EpisodePairingResolutionService`) — mesma exceção que `videofile` já tem.
- **`mediafolder`**: `MediaFolderService.listFolders` embute `AnimeBindingSummary` via `AnimeService.getAnimeBindings`; `Anime.folder` referencia `MediaFolder`.
- **`videofile`**: `VideoFileService.listVideoFiles`/`listVideoFilesOrderedByNaturalSortKey` embutem `TmdbFilePairingResponse` via `AnimeTmdbFilePairingService.getTmdbFilePairings`; `AnimeEpisodeService` delega a listagem de episódios inteira pro `VideoFileService.listVideoFilesOrderedByNaturalSortKey`.
- **`anilist`** e **`tmdb`**: fontes de dados externos consumidas via seus `*SearchService`.
- **`anibridge`**: `AnimeTmdbProposalService` usa `AnibridgeResolutionService.resolveBestCandidate` pra propor o binding TMDB.
- **`setting`**: fornece a preferência de idioma usada no pré-preenchimento de imagens TMDB.

## Pontos de entrada

HTTP, via os seis `*Resource` (`/v1/metadata/animes/...`, `/v1/metadata/folders/{folderId}/anime`, `/v1/metadata/anime-relations`, `/v1/metadata/files/{fileId}/tmdb-pairing`, `/v1/metadata/animes/{anilistId}/episodes`, `/v1/metadata/tmdb/binding-proposals`) — ver `openapi.yaml`, tags `Anime`, `AnimeRelations`, `AnimeTmdb`, `AnimeTmdbFilePairing`, `AnimeEpisodes`, `AnimeTmdbProposal`.

Programático, pra outros domínios (sem HTTP):
- `AnimeService.findByFolder`, `.exists`, `.find`, `.findOrThrow`, `.updateReviewStatusIfNotReviewed`, `.getAnimeBindings`.
- `AnimeTmdbFilePairingService.getTmdbFilePairings`.
- `AnimeTmdbService.findTvBoundAnimeOrThrow` (uso intra-domínio, mas público).

## Fluxos importantes

**Bind inicial (`AnimeService.bindAnimeToFolder`):** valida que não existe metadata pro anime nem binding prévio da pasta → busca na AniList → persiste `Anime` com `franchiseRootCuration = AUTO` → chama `AnimeRelationsService.syncRelations` pra popular o grafo.

**Sync de relações em cascata (`AnimeRelationsService.syncRelations`):** apaga as arestas que saem do anime e as recria a partir da resposta da AniList; pra cada relação que aponta pra um anime *já conhecido localmente*, salva a aresta e dispara recursivamente o mesmo sync pro vizinho (se ainda não visitado nessa chamada), buscando-o de novo na AniList. Relações pra animes não vinculados localmente são ignoradas — o grafo se fecha quando esses animes forem vinculados depois. Tudo dentro de uma única `@Transactional`.

**Grafo de relações (`AnimeRelationsService.getAnimeRelationGraph`):** BFS bidirecional sobre `AnimeRelation` (`RelationGraph.closure`, segue tanto `fromAnilistId` quanto `toAnilistId`, uma consulta por nível) até `depth` saltos, classificando cada nó como raiz manual/natural/não-raiz via `RelationType.ANCESTOR_LINKS` (`PREQUEL`, `PARENT`).

**Franquia organizada (`AnimeRelationsService.getAnimeFranchise`):** os percursos do grafo ficam em `RelationGraph` (`findFranchiseRootId`, `closure`, `sequelChainFrom`); o service só monta a resposta. Sobe até a raiz seguindo `RelationType.ANCESTOR_LINKS` (saindo do pivot) e `SUMMARY` em sentido invertido (chegando no pivot — uma aresta `A -[SUMMARY]-> B` indica que B é derivado de A); `ALTERNATIVE` fica de fora dessa subida porque a AniList costuma taguear esse tipo nos dois sentidos, o que criaria um ciclo sem lado ancestral objetivo. A partir da raiz, fecha a franquia via BFS bidirecional sem limite de profundidade, só por `RelationType.FRANCHISE_LINKS` (`SEQUEL`, `SIDE_STORY`, `SPIN_OFF`, `SUMMARY`, `PARENT`, `ALTERNATIVE`). `seasons` é a cadeia de `SEQUEL` a partir da raiz, na ordem da cadeia, filtrada por `RelationType.SERIES_FORMATS`; o resto do fechamento vira `movies`/`specials`, separados por `RelationType.MOVIE_FORMATS`/`SPECIAL_FORMATS` (três listas disjuntas) e ordenados por `seasonYear`, depois `Season` (ordem natural do enum), depois `anilistId`. `root`/`seasons`/`movies`/`specials` usam o `AnimeResponse` completo, resolvido via `AnimeOverrideService` igual às demais vistas do domínio. Busca os `Anime` da franquia em lote (`Anime.findByIds`) pra evitar N+1.

**Resolução de overrides (`AnimeOverrideService`):** usada por `AnimeService.toResponse`/`toBindingSummary` e por `AnimeRelationsService` pra decidir, campo a campo, entre o override manual (TMDB) e o dado cru (AniList); banner e logo só existem via override (a AniList não tem esses campos).

**Bind TMDB (`AnimeTmdbService.bindTmdb`):** valida que o anime existe e ainda não tem binding → confirma o `tmdbId` no TMDB pro `mediaType` informado → grava o binding → `prefillImageOverrides` tenta preencher backdrop/logo com a imagem mais votada no idioma preferido (`SettingService`), com `RetryUtils.withRateLimitRetry`; falha aqui é engolida (log + segue sem imagem), nunca aborta o bind.

**Orquestração automática (`ingestion.FolderAutoBindService`):** varre uma pasta recém-detectada e encadeia bind AniList → bind TMDB → pairing de episódios, usando `AnimeService.updateReviewStatusIfNotReviewed` pra marcar o resultado (`PARTIAL`/`PENDING_REVIEW`) sem sobrescrever uma revisão manual já confirmada.

## Arquivos críticos

- `model/Anime.java` — entidade central; PK é o próprio `anilistId` (sem id surrogate), concentra dados AniList + binding/overrides TMDB + curadoria + revisão.
- `service/AnimeOverrideService.java` — motor único de resolução override↔dado cru, reaproveitado pelas outras facetas.
- `service/AnimeService.java` — binding↔pasta, sync, curadoria; ponto de entrada mais usado por outros domínios.
- `service/AnimeRelationsService.java` — sync recursivo do grafo e montagem das respostas de grafo e franquia (`getAnimeRelationGraph`, `getAnimeFranchise`).
- `graph/RelationGraph.java` — percursos sobre o grafo de relações: fechamento por BFS, subida até a raiz da franquia, cadeia de `SEQUEL` e classificação de raiz; regras de ancestralidade (`SUMMARY` invertido, `ALTERNATIVE` fora). Classe estática sem CDI, não `*Service`, porque não orquestra regra de negócio nem tem resource — só lê `AnimeRelation`/`Anime` pelas consultas das entidades, dentro da transação de quem chama. Lógica mais complexa do domínio.
- `graph/model/RelationClosure.java` — resultado da BFS (`animeIds` alcançados + `edges` percorridas).
- `service/AnimeTmdbService.java` — binding TMDB, overrides, schema de opções; maior arquivo do domínio (344 linhas).
- `service/AnimeTmdbFilePairingService.java` — pairing por arquivo e schema de temporadas/episódios.
- `model/AnimeRelation.java` + `model/RelationType.java` — grafo de relações; `RelationType` concentra as listas-fonte-única usadas pelo grafo e pela franquia (`ANCESTOR_LINKS`, `FRANCHISE_LINKS`, `SPECIAL_LINKS`, `SERIES_FORMATS`, `MOVIE_FORMATS`, `SPECIAL_FORMATS`).
- `model/Season.java` — enum da estação de estreia (`WINTER`/`SPRING`/`SUMMER`/`FALL`), usado só pra ordenar `movies`/`specials` da franquia sem uma data de estreia mais precisa persistida localmente.

## Observações técnicas e débitos identificados

- **Uso inconsistente de `RetryUtils`:** dentro do domínio, só têm retry de rate limit o `AnimeTmdbService.prefillImageOverrides` e a busca de episódios das temporadas nas duas estratégias de pairing (`EpisodePairingResolutionService`, `EpisodeContinuityResolutionService`), via `TmdbService.getSeasonEpisodes`. No caminho automático da ingestão, o `FolderAutoBindService` envolve com `RetryUtils.withRateLimitRetry` as chamadas a `bindAnimeToFolder` e `bindTmdb`. As demais chamadas de rede — `anilistSearchService.findById` (bind e sync pelo portal, cascata de `syncRelations`, proposta de binding), `tmdbService.getTvDetails/getMovieDetails` (bind e estado TMDB, schemas, temporadas da continuidade), traduções/imagens do schema de overrides e `tmdbService.getSeason` (file-pairing schema) — não têm retry nem tratamento de rate limit. Sob rate limiting da AniList/TMDB, esses caminhos falham direto. **Backlog real** (confirmado pelo mantenedor, mesmo item do risco geral de retry ad hoc).
- **`syncRelations` roda recursão não limitada dentro de uma única `@Transactional`:** cada vizinho ainda não visitado dispara uma nova chamada de rede à AniList antes de continuar a recursão. Em franquias grandes, isso significa N chamadas de rede sequenciais dentro de uma transação de banco só, sem timeout ou circuit breaker visível — risco de transação longa/timeout proporcional ao tamanho da franquia. **Backlog real** (confirmado pelo mantenedor).
- **`getTmdbFilePairingSchema` faz uma chamada por temporada** (`tmdbService.getSeason`), sequencial, um N+1 clássico sobre a API do TMDB — **confirmado**: `getSeason` é cache-first via `IntegrationCache` (mesmo padrão dos demais métodos de `TmdbService`), então chamadas repetidas pela mesma temporada não batem de novo no upstream; o N+1 em si (N chamadas sequenciais na primeira vez) continua existindo, mas o custo de rede real só ocorre uma vez por temporada por TTL.
- **`clearTmdbBinding` não toca em `TmdbFilePairing`:** ao remover o binding TMDB, os overrides do anime são zerados, mas os pairings de episódio dos vídeos da pasta (nome, sinopse, thumbnail vindos do TMDB) permanecem intactos, agora desvinculados de qualquer binding TMDB ativo. **Lacuna a cobrir** (confirmado pelo mantenedor): o comportamento correto é limpar/invalidar os pairings junto ao remover o binding; vai pro backlog.
- **Sem paginação em lookups batch:** `AnimeService.getAnimeBindings` e `AnimeTmdbFilePairingService.getTmdbFilePairings` recebem a lista inteira de ids do chamador (`mediafolder`/`videofile`) sem limite — hoje acompanha o tamanho da página do chamador, mas não há um teto próprio caso isso mude. **Backlog real** (confirmado pelo mantenedor).
- Nenhum `TODO`/`FIXME`/`XXX` encontrado no domínio.
- Tratamento de concorrência no bind TMDB/AniList: não há lock explícito nem verificação de condição de corrida entre a checagem de duplicidade (`Anime.find`/`anime.tmdbId != null`) e o `persist()`/commit — duas requisições simultâneas pro mesmo `anilistId` podem ambas passar a validação antes de uma persistir (mitigado só pela constraint de PK no banco, que faria a segunda falhar com exceção não tratada especificamente aqui). **Backlog real** (confirmado pelo mantenedor, mesmo item do risco geral de corrida no bind).
- Boa prática observada: leituras usadas fora do contexto de request HTTP (`findByFolder`, `exists`, `find` em `AnimeService`) foram corretamente marcadas `@Transactional` mesmo sendo apenas leitura, já que `ingestion` as chama fora de uma transação ambiente — mostra atenção deliberada a esse detalhe, não um padrão acidental.
