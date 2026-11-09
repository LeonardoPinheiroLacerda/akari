# Domínio `anime`

## Objetivo do módulo

Manter a metadata de anime do akari: o vínculo entre uma pasta física do catalog (`mediafolder`) e a identidade canônica do título na AniList, mais uma camada opcional de enriquecimento via TMDB (imagens de melhor qualidade, traduções, overrides manuais e pairing de temporada/episódio por arquivo de vídeo). É o domínio mais central do projeto: concentra a identidade de cada anime que os demais domínios (ingestão, catalog, player) consultam.

## Responsabilidade principal

O domínio é organizado em dois eixos mais uma faceta de listagem que combina os dois, refletidos nas tags do contrato (`Anime`, `AnimeRelations`, `AnimeFranchise`, `AnimeTmdb`, `AnimeTmdbFilePairing`, `AnimeEpisodes`):

- **Eixo base (Anime):** binding anime↔pasta, fetch/sync na AniList, status de revisão da ingestão, organização dos animes em franquias, o grafo de relações entre animes (sequel/prequel/side story) e a franquia organizada em raiz/temporadas/filmes/especiais pra tela de detalhe do app.
- **Eixo TMDB (enriquecimento opcional):** binding TMDB↔anime, overrides manuais de título/sinopse/poster/backdrop/logo, e pairing por arquivo de vídeo (temporada/episódio/thumbnail). Um anime funciona sem TMDB; só banner e logo do `AnimeResponse` ficam `null` sem binding + overrides.
- **Episódios (AnimeEpisodes):** resolve a pasta do anime (eixo base) e delega a listagem em si pro domínio `videofile`, já em ordem natural de nome — pronta pra tela de episódios do app, sem o cliente precisar descobrir `folderId` nem reordenar. O pareamento TMDB (eixo TMDB) só enriquece cada item (via `videofile`), não define mais a posição.

Cada faceta bem distinta tem seu próprio par resource/service, todos compartilhando o mesmo `model/` (principalmente `Anime`), conforme a convenção do projeto pra domínios com múltiplos resources:

| Resource | Service | Faceta |
|---|---|---|
| `AnimeResource` | `AnimeService` | binding↔pasta, sync AniList, revisão |
| `AnimeRelationsResource` | `AnimeRelationsService` | grafo de relações, raízes de franquia, franquia organizada em raiz/temporadas/filmes/especiais |
| `AnimeFranchiseResource` | `AnimeFranchiseService` | organização administrativa das franquias: listar, criar/remover franquia manual, mover ou ocultar um anime, ocultos |
| `AnimeTmdbResource` | `AnimeTmdbService` | binding TMDB, overrides, schema de overrides |
| `AnimeTmdbFilePairingResource` | `AnimeTmdbFilePairingService` | pairing TMDB por arquivo, schema de temporadas/episódios |
| `AnimeTmdbProposalResource` | `AnimeTmdbProposalService` | propostas de binding TMDB (anibridge) e de pairing de episódios (anibridge ou continuidade), só consulta |
| `AnimeEpisodeResource` | `AnimeEpisodeService` | listagem paginada de episódios (arquivos de vídeo) do anime, em ordem natural de nome |

`EpisodePairingResolutionService`, as etapas dele em `anime/pairing` e o `AbsoluteEpisodeOffsetService` também não têm resource próprio: são a resolução do pairing TMDB dos arquivos de uma pasta pela estratégia do anibridge, usada pela proposta de pairing do portal (`AnimeTmdbProposalService`, que só devolve) e pelo pairing automático do step `TMDB_FILEPAIR` da ingestão (`AnimeTmdbFilePairingFillService`, que grava) — moram aqui, junto do pairing, e não em `ingestion` (que já depende de `anime`; o contrário faria um ciclo de services). `EpisodeContinuityResolutionService` (estratégia de continuidade) e `EpisodePairingComparisonService` (comparação da resolução com os pares gravados), também usados só pela proposta, também não têm resource.

`AnimeTmdbFilePairingFillService` também não tem resource: grava o pairing dos arquivos resolvidos pelo `EpisodePairingResolutionService` pro step `TMDB_FILEPAIR` da ingestão, só onde não há decisão a tomar (arquivo sem pairing, episódio sem conflito).

`AnimeTmdbOverridesFillService` também não tem resource: preenche os overrides a partir do TMDB pro step `TMDB_OVERRIDES` da ingestão, fora do `AnimeTmdbService` pra não inchar o service do binding com as regras de escolha por idioma.

`AnimeOverrideService` **não tem resource próprio** — confirmado no código: é um `@ApplicationScoped` puro, sem endpoint HTTP, que resolve campo a campo qual valor prevalece (override manual do TMDB, quando setado, ou o dado cru da AniList). Existe pra não duplicar essa regra nos services que exibem `Anime`: também monta o `AnimeResponse` (`toResponse`, com o `franchiseRootId` da resolução de franquias) e o `AnimeBindingSummary` (`toBindingSummary`) usados por `AnimeService`, `AnimeRelationsService` e `AnimeFranchiseService`, que o injetam diretamente — o `AnimeRelationsService` também resolve título/thumbnail dos `AnimeRelationNode`; `AnimeTmdbService`/`AnimeTmdbFilePairingService` não precisam dele porque não exibem o anime resolvido, só o estado de binding/pairing cru.

## Funcionalidades existentes

Eixo base:
- Vincular anime (por `anilistId`) a uma pasta do catalog, buscando os dados na AniList (`bindAnimeToFolder`).
- Remover a metadata de um anime, liberando a pasta (`deleteAnime`). Os animes `MANUAL_MEMBER` na franquia dele voltam pra `AUTO` (`Anime.resetManualMembers`).
- Ler a metadata local por `anilistId` ou por `folderId` (`getAnime`, `getAnimeByFolder`).
- Ressincronizar a metadata com a AniList, preservando pasta e atribuição de franquia (`syncAnime`).
- Confirmação manual de revisão da ingestão (`markAsReviewed`), que trava o campo contra recálculos automáticos.
- Resumir o binding de várias pastas numa chamada (`getAnimeBindings`), consumido por `mediafolder` pra embutir `animeBinding` na listagem de pastas.

Grafo de relações:
- Sincronizar as arestas do grafo a partir da resposta da AniList, em cascata pros vizinhos já conhecidos localmente (`syncRelations`).
- Materializar o grafo via BFS bidirecional até profundidade configurável (`getAnimeRelationGraph`).
- Listar as raízes das franquias (naturais e manuais), paginadas (`listRelationRoots`).
- Montar a franquia de um anime já organizada em raiz/temporadas/filmes/especiais pra tela de detalhe do app (`getAnimeFranchise`); `404` se o anime está fora de qualquer franquia.

Organização em franquias (`AnimeFranchiseService`, portal):
- Listar as franquias, com busca no título da raiz e dos membros e filtro por tipo de raiz (`listFranchises`).
- Criar uma franquia manual com um anime como raiz, que sai de onde estava com o galho automático dele (`createFranchise`); remover uma franquia manual, voltando a raiz (`MANUAL_ROOT`) e os membros `MANUAL_MEMBER` pra `AUTO` (`deleteFranchise`).
- Detalhar uma franquia pra organização: membros com atribuição e pai automático, animes relacionados em outra franquia ou ocultos, arestas pro desenho e prévia da classificação (`getFranchise`).
- Mover um anime de franquia (`MANUAL_MEMBER`), devolvê-lo à regra automática (`AUTO`) ou ocultá-lo (`HIDDEN`) (`setAnimeFranchise`); mover uma raiz manual junta as duas franquias.
- Listar os animes fora de qualquer franquia, ocultos à mão ou por herança (`listHiddenAnimes`).
- Achar o prequel direto de um anime, usado pelo `AbsoluteEpisodeOffsetService` pra calcular o deslocamento de numeração absoluta no pairing de episódios (`findDirectPrequelId`).

Eixo TMDB:
- Vincular o anime a uma série/filme do TMDB, validando a existência do id (`bindTmdb`). O bind não preenche os overrides de imagem.
- Remover o binding TMDB, limpando junto os overrides e os pairings TMDB de todos os arquivos da pasta (`clearTmdbBinding`, via `TmdbFilePairing.deleteByFolderId`) — os pairings apontam pra temporadas/episódios do título desvinculado. Os pairings precisam ser refeitos depois de um novo bind.
- Ler o estado TMDB atual (binding + overrides) (`getTmdbState`). O estado devolvido por `getTmdbState`, `bindTmdb` e `updateTmdbOverrides` traz sempre `details`, a ficha resumida da série/filme vinculado como está no TMDB (título, título original, data, temporadas/episódios, pôster e backdrop principais, sem overrides), pra conferir se o binding aponta pro título certo. A ficha é lida cache-first, sem `language` (textos no idioma padrão do TMDB, `en-US`); se o TMDB falhar, a operação falha com o erro do TMDB (`502`/`503`) — no `updateTmdbOverrides`, desfazendo a gravação dos overrides.
- Substituir o bloco de overrides inteiro (`updateTmdbOverrides`).
- Preencher título, sinopse, backdrop e logo a partir das traduções e imagens do TMDB (`AnimeTmdbOverridesFillService.fillOverrides`), chamado pelo step `TMDB_OVERRIDES` da ingestão — regras em "Fluxos importantes".
- Montar o schema de opções concretas por campo overridable: traduções de título/sinopse e galeria de imagens do TMDB (`getTmdbOverridesSchema`).

Proposta de binding TMDB:
- Propor a série/filme do TMDB pra um `anilistId` (`POST /v1/metadata/tmdb/binding-proposals`, `AnimeTmdbProposalService.proposeTmdbBinding`), sem gravar nada: o melhor candidato do anibridge (`AnibridgeResolutionService.resolveBestCandidate`), com o `malId` (fallback do anibridge) e o total de episódios (desempate entre candidatos) lidos da AniList (`AnilistSearchService.findById`, cache-first). Por isso funciona mesmo com o anime ainda não salvo no akari. A resposta traz a mesma ficha `details` do estado TMDB (`AnimeTmdbService.fetchDetails`), que também confirma que o `tmdbId` existe. `404` se o anime não existir na AniList, se o anibridge não tiver mapeamento pra ele ou se o `tmdbId` não existir mais no TMDB. Pra efetivar, o portal chama o `bindTmdb` com o `tmdbId`/`mediaType` propostos.
- Propor o pairing TMDB de cada arquivo da pasta de um anime já vinculado a uma série do TMDB (`POST /v1/metadata/tmdb/file-pairing-proposals`, `AnimeTmdbProposalService.proposeTmdbFilePairing`), sem gravar nada, pela `strategy` pedida: `ANIBRIDGE` (padrão, resolução pelo anibridge — `EpisodePairingResolutionService`) ou `CONTINUITY` (`EpisodeContinuityResolutionService`, ver abaixo). Devolve todos os arquivos da pasta na ordem natural dos nomes, com o `status` de cada um (`PAIRED` ou o motivo de não ter proposta; quais status pertencem a qual estratégia está no contrato).
- Sobre o resultado das duas estratégias, o `EpisodePairingComparisonService` compara com o que já está gravado, sem saber qual estratégia gerou a resolução: par atual do arquivo (`currentSeasonNumber`/`currentEpisodeNumber`), `change` (`NEW` sem par gravado, `SAME` igual ao gravado, `REPLACE` diferente), `usedByFileId` (outro arquivo que já usa o episódio proposto — pelo par gravado, ou por ter recebido a mesma proposta antes na ordem natural) e `selectedByDefault` (só `NEW` sem uso por outro arquivo, em qualquer temporada). Os pares gravados da pasta são lidos uma vez (`EpisodePairingComparisonService.findCurrentEpisodes`) e servem tanto à comparação quanto às âncoras da continuidade. `404` se o anime não existir ou não tiver binding TMDB; `400` se o binding for de filme; `502`/`503` se a estrutura da série não puder ser lida do TMDB na continuidade.

Resolução do pairing TMDB (`EpisodePairingResolutionService`, sem endpoint próprio). O service só encadeia três etapas, cada uma numa classe de `anime/pairing` — regra nova da resolução entra na etapa a que pertence, não no service:

1. **Numeração** (`FolderEpisodeNumbering`, classe utilitária estática): o número de episódio de cada arquivo, pelo `EpisodeNumberExtractor.extract` (domínio `ingestion`), olhando a pasta inteira quando ele devolve o mesmo número em mais de um arquivo (temporada ou número do título lido como episódio). Nesse caso, troca o número só desses arquivos pelo da posição, entre os números isolados do nome (`extractSeparatedNumbers`), que tem um valor diferente em cada um — os arquivos de número único mantêm o seu, desde que nada se repita no fim. Sem essa posição, numera pela ordem dos nomes (trechos numéricos comparados como números), só com a pasta no total de episódios do anime e todos os nomes com a mesma quantidade de números — sem essas travas, um arquivo extra ou de outro lançamento deslocaria os seguintes sem aviso. Nada disso depende da ordem em que os arquivos chegam: a ordem natural da listagem também sai do `extract` e fica embaralhada justamente nessas pastas.
2. **Conversão** (`AnibridgeEpisodeMapper`, que prepara por pasta um `AnibridgeFolderMapping`): número de episódio → temporada/episódio do TMDB pelo anibridge, só pelos segments que apontam pra série vinculada (`AnibridgeResolutionService.resolveCandidateFor`), pra nunca aplicar a numeração de um título sobre outro: sem nenhum, todos os arquivos saem `NO_MAPPING`; segments conflitantes (`resolveSafeSegments` vazio) → `CONFLICTING_MAPPING`. A numeração absoluta fica nesta etapa, e não numa à parte, porque o offset só vale nos segments que cobrem a temporada isolada com segurança:
   - offsets candidatos pela cadeia de prequels (`AbsoluteEpisodeOffsetService`, lida do banco e, pro prequel ou aresta que ainda não estiver salvo, da AniList cache-first — senão o resultado dependeria da ordem em que a ingestão processa as pastas da franquia);
   - pasta inteira: se algum arquivo não resolve direto, entre os offsets menores que todos os números da pasta fica o que resolve mais arquivos (desempate pelo mais próximo), e ele vale pra todos — inclusive o cujo número também caberia no intervalo do anime e seria resolvido direto, no episódio errado (ex.: no `86 Eighty Six Part 2`, offset 11, o `S01E12` vira o episódio 1 → S1E12, e não o 12 → S1E23), e o que um offset mais próximo resolveria errado (ex.: no `Boku no Hero Academia 7`, arquivos 139–159, o offset 125 resolve só 139–146, e o 138 resolve todos). Sem offset elegível (pasta com número ≤ offset, que indica numeração local misturada ou prequel contado com um episódio a mais, como um `#00`), cada arquivo segue sozinho;
   - arquivo sozinho: número direto e, sem resultado, os offsets do mais próximo ao mais distante, só quando o mapeamento cobre a temporada isolada com segurança — senão `AMBIGUOUS_ABSOLUTE_NUMBERING`; pasta de 1 arquivo com anime de 1 episódio → episódio 1, como último recurso.
3. **Validação** (`TmdbEpisodeValidator`): o episódio é confirmado no TMDB (`TmdbService.getSeasonEpisodes`, com retry de rate limit, temporada cacheada por pasta), de onde sai a metadata do pairing; se não existir (temporada inexistente, episódio fora dela ou falha na busca) → `NOT_FOUND_IN_TMDB`, sem pairing. Ele também precisa ter ido ao ar perto do período de exibição do anime na AniList: até 365 dias antes do `startDate` e até 365 dias depois do `endDate` (sem `endDate`, só o início limita; data parcial vale pelo período inteiro que cobre; sem data do episódio no TMDB, não há o que comparar e ele é aceito). Fora disso → `OUT_OF_AIR_WINDOW`, sem pairing — pega o episódio de outra temporada ou de outro anime da franquia (ex.: os arquivos 278+ do `FAIRY TAIL (2018)`, resolvidos pra temporada 5, de 2014). A folga é grande porque datas de streaming, DVD e especiais costumam se afastar da exibição na TV por meses.

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
- `AnimeFranchiseService` injeta `AnimeService` (reusa `findOrThrow`), `AnimeOverrideService` (monta as respostas exibidas) e `AnimeRelationMapper` (arestas do detalhe).
- `AnimeService`, `AnimeRelationsService` e `AnimeFranchiseService` usam `graph/FranchiseResolver` pra preencher `franchiseRootId` de cada `AnimeResponse`; os dois últimos também usam `graph/FranchiseClassifier`.
- `AnimeTmdbService` injeta `AnimeService` (busca o anime base, reusa `findOrThrow`).
- `AnimeTmdbFilePairingService` injeta `AnimeTmdbService` (reusa `findTvBoundAnimeOrThrow`).
- `AnimeTmdbFilePairingFillService` injeta `AnimeTmdbService` (reusa `findTvBoundAnimeOrThrow`) e `EpisodePairingResolutionService` (resolução pelo anibridge).
- `AnimeTmdbOverridesFillService` injeta `AnimeTmdbService` (reusa `findBoundAnimeOrThrow`, `fetchTranslations` e `fetchImages`, estes dois package-private).
- `AnimeTmdbProposalService` injeta `AnimeTmdbService` (reusa `fetchDetails` pra montar a ficha e `findTvBoundAnimeOrThrow`), `EpisodePairingResolutionService` (estratégia anibridge), `EpisodeContinuityResolutionService` (estratégia de continuidade) e `EpisodePairingComparisonService` (pares gravados e comparação) — só escolhe a estratégia e encadeia os três.
- `AnimeTmdbFilePairingMapper.toPairingRequest` é o único ponto que traduz um episódio do TMDB pros campos do pairing — usado por `EpisodePairingResolutionService` e `EpisodeContinuityResolutionService`.
- `EpisodePairingResolutionService` injeta `AnibridgeEpisodeMapper` e `TmdbEpisodeValidator` (`anime/pairing`), as etapas da resolução.
- `AnibridgeEpisodeMapper` injeta `AbsoluteEpisodeOffsetService` (offsets de numeração absoluta).
- `AbsoluteEpisodeOffsetService` injeta `AnimeService` e `AnimeRelationsService` (anda a cadeia de prequels salva no banco).
- `AnimeEpisodeService` injeta `AnimeService` (resolve o anime e a pasta).

**Externas (outros domínios), por service:**
- `AnimeService`: `anilist.service.AnilistSearchService` (fetch/sync), `mediafolder.service.MediaFolderService` (resolve a pasta no bind).
- `AnimeRelationsService`: `anilist.service.AnilistSearchService` (busca vizinhos durante o sync em cascata).
- `AnimeTmdbService`: `tmdb.service.TmdbService` (detalhes, imagens, traduções).
- `AnimeTmdbOverridesFillService`: `setting.service.SettingService` (idiomas do título, da sinopse, do logo e de fallback); traduções e imagens do TMDB vêm pelo `AnimeTmdbService`.
- `AnimeTmdbFilePairingService`: `tmdb.service.TmdbService` (temporadas/episódios), `videofile.service.VideoFileService` (valida o arquivo).
- `AbsoluteEpisodeOffsetService`: `anilist.service.AnilistSearchService` (prequel e total de episódios que ainda não estão salvos, com retry de rate limit).
- `AnimeTmdbProposalService`: `anilist.service.AnilistSearchService` (`malId` e total de episódios), `anibridge.service.AnibridgeResolutionService` (melhor candidato TMDB).
- `EpisodePairingResolutionService`: `videofile.service.VideoFileService` (arquivos da pasta).
- `anime/pairing`: `anibridge.service.AnibridgeResolutionService` (candidato da série vinculada, segments, conversão de episódio — `AnibridgeEpisodeMapper`/`AnibridgeFolderMapping`), `tmdb.service.TmdbService` (temporadas/episódios — `TmdbEpisodeValidator`) e a classe utilitária `ingestion.extractor.EpisodeNumberExtractor` (número do episódio no nome do arquivo — `FolderEpisodeNumbering`).
- `EpisodeContinuityResolutionService`: `videofile.service.VideoFileService` (arquivos da pasta em ordem natural), `tmdb.service.TmdbService` (ficha e temporadas da série).
- `AnimeEpisodeService`: `videofile.service.VideoFileService` (`listVideoFilesOrderedByNaturalSortKey` — listagem em si, já com `tmdbPairing` embutido).
- Model `Anime` referencia `mediafolder.model.MediaFolder` via `@OneToOne`.

O acoplamento alto do domínio como um todo (7 domínios externos: `anilist`, `tmdb`, `anibridge`, `mediafolder`, `videofile`, `setting` e `ingestion`, este só pelo `EpisodeNumberExtractor`) é distribuído entre as facetas; a resolução do pairing (`anime/pairing`) é a parte mais acoplada (`anibridge`, `tmdb` e o extractor do `ingestion`), dividida em etapas pra que cada classe dependa só do que a sua etapa usa.

## Módulos relacionados

- **`ingestion`** usa `AnimeService` no step `ANILIST_SYNC` (`AnilistSyncStepService`): `findByFolder` pra manter um vínculo já existente, `find` pra recusar um título já sincronizado com outra pasta e `bindAnime` pra vincular; no step `TMDB_BIND` (`TmdbBindStepService`), usa `AnimeTmdbService.fetchDetails` pra conferir se o título escolhido existe no TMDB e `AnimeTmdbService.bindTmdb` pra vincular o anime; no step `TMDB_OVERRIDES` (`TmdbOverridesStepService`), usa `AnimeTmdbOverridesFillService.fillOverrides` pra preencher os overrides; no step `TMDB_FILEPAIR` (`TmdbFilePairStepService`), usa `AnimeTmdbFilePairingFillService.fillPairings` pra parear os arquivos; o fim de cada task grava o anime da pasta (`findByFolder`) em `IngestionTask.anime` (`anime_id`, com `ON DELETE SET NULL` — remover o anime só limpa o vínculo da task). No sentido contrário, `anime` usa de `ingestion` só o `extractor.EpisodeNumberExtractor` (no `EpisodePairingResolutionService`) — mesma exceção que `videofile` já tem.
- **`mediafolder`**: `MediaFolderService.listFolders` embute `AnimeBindingSummary` via `AnimeService.getAnimeBindings`; `Anime.folder` referencia `MediaFolder`.
- **`videofile`**: `VideoFileService.listVideoFiles`/`listVideoFilesOrderedByNaturalSortKey` embutem `TmdbFilePairingResponse` via `AnimeTmdbFilePairingService.getTmdbFilePairings`; `AnimeEpisodeService` delega a listagem de episódios inteira pro `VideoFileService.listVideoFilesOrderedByNaturalSortKey`.
- **`anilist`** e **`tmdb`**: fontes de dados externos consumidas via seus `*SearchService`.
- **`anibridge`**: `AnimeTmdbProposalService` usa `AnibridgeResolutionService.resolveBestCandidate` pra propor o binding TMDB, passando o formato do anime na AniList pro desempate entre filme e série.
- **`setting`**: `AnimeTmdbOverridesFillService` injeta o `SettingService` pra ler os idiomas do preenchimento dos overrides.

## Pontos de entrada

HTTP, via os sete `*Resource` (`/v1/metadata/animes/...`, `/v1/metadata/folders/{folderId}/anime`, `/v1/metadata/anime-relations`, `/v1/metadata/files/{fileId}/tmdb-pairing`, `/v1/metadata/animes/{anilistId}/episodes`, `/v1/metadata/tmdb/binding-proposals`, `/v1/metadata/franchises`) — ver `openapi.yaml`, tags `Anime`, `AnimeRelations`, `AnimeFranchise`, `AnimeTmdb`, `AnimeTmdbFilePairing`, `AnimeEpisodes`, `AnimeTmdbProposal`.

Programático, pra outros domínios (sem HTTP):
- `AnimeService.getAnimeBindings` (usado por `mediafolder`), `.findByFolder`, `.find` e `.bindAnime` (usados por `ingestion`); `AnimeTmdbService.bindTmdb` (usado por `ingestion`, além do resource) e `.fetchDetails` (usado por `ingestion` e pelo `AnimeTmdbProposalService`); `AnimeTmdbOverridesFillService.fillOverrides` e `AnimeTmdbFilePairingFillService.fillPairings` (usados por `ingestion`). `AnimeService.findOrThrow` e `AnimeTmdbService.findBoundAnimeOrThrow` também são públicos, mas só usados dentro do domínio `anime`.
- `AnimeTmdbFilePairingService.getTmdbFilePairings`.
- `AnimeTmdbService.findTvBoundAnimeOrThrow` (uso intra-domínio, mas público).

## Fluxos importantes

**Bind inicial (`AnimeService.bindAnimeToFolder`):** valida que não existe metadata pro anime nem binding prévio da pasta → busca na AniList → persiste `Anime` com `franchiseAssignment = AUTO` → chama `AnimeRelationsService.syncRelations` pra popular o grafo. `AnimeService.bindAnime` é o mesmo bind devolvendo o model, sem montar a resposta (e sem a resolução de franquias dela).

**Sync de relações em cascata (`AnimeRelationsService.syncRelations`):** apaga as arestas que saem do anime e as recria a partir da resposta da AniList; pra cada relação que aponta pra um anime *já conhecido localmente*, salva a aresta e dispara recursivamente o mesmo sync pro vizinho (se ainda não visitado nessa chamada), buscando-o de novo na AniList. Relações pra animes não vinculados localmente são ignoradas — o grafo se fecha quando esses animes forem vinculados depois. Tudo dentro de uma única `@Transactional`.

**Grafo de relações (`AnimeRelationsService.getAnimeRelationGraph`):** BFS bidirecional sobre `AnimeRelation` (`RelationGraph.closure`, segue tanto `fromAnilistId` quanto `toAnilistId`, uma consulta por nível) até `depth` saltos, classificando cada nó como raiz manual/natural/não-raiz pela resolução de franquias (`FranchiseResolution.rootKindOf`).

**Resolução de franquias (`graph/FranchiseResolver`):** cada anime pertence a no máximo uma franquia, identificada pela raiz. Resolve a coleção inteira de uma vez, em memória, a cada leitura (atribuição de cada anime via `Anime.findFranchiseNodes`, arestas de `RelationType.AUTO_PARENT_LINKS`) — duas consultas leves; não há coluna materializada, então nada fica desatualizado depois de um sync de relações, bind ou unbind. O **pai automático** de um anime é, em ordem de prioridade, o `PREQUEL` saindo dele, o `PARENT` saindo dele, ou quem aponta pra ele com `SIDE_STORY`/`SPIN_OFF`/`SUMMARY` (`RelationType.DERIVED_LINKS`), desempatando pelo ano mais antigo e depois pelo menor `anilistId`; `ALTERNATIVE`, `OTHER` e `CHARACTER` nunca geram pai. A **franquia** (`franchiseOf`), pela atribuição (`Anime.franchiseAssignment`, que diz como o anime foi posicionado): `HIDDEN` → nenhuma; `MANUAL_ROOT` → ela mesma (raiz manual, mesmo que ganhe pai num sync); `MANUAL_MEMBER` → a franquia da raiz escolhida (`Anime.franchiseRoot`) — se ela deixar de ser raiz (moveu, foi oculta, voltou pra `AUTO`), o anime a acompanha; `AUTO` sem pai → ela mesma (raiz natural); `AUTO` com pai → a franquia do pai, então o galho segue o pai (se o pai é movido ou oculto, vai junto). Ciclo na subida → o anime onde o ciclo foi detectado vira raiz. O resultado (`graph/model/FranchiseResolution`) responde a franquia de cada anime, as raízes (`NATURAL`/`MANUAL`), os membros de cada franquia e os animes fora de todas. Depois de uma mudança de organização (`AnimeFranchiseService.createFranchise`/`setAnimeFranchise`), `FranchiseResolver.resolveAgain` resolve de novo reaproveitando o pai automático da resolução anterior — atribuição não mexe nas arestas nem no ano — e relê só a atribuição dos animes.

**Franquia organizada (`AnimeRelationsService.getAnimeFranchise`):** os membros são os animes cuja franquia é a raiz do anime pedido; `graph/FranchiseClassifier` divide: `seasons` é a raiz mais a cadeia de `SEQUEL` a partir dela só entre os membros (`RelationGraph.sequelChainFrom`; a cadeia atravessa membros de outro formato, que só não entram), filtrada por `RelationType.SERIES_FORMATS`; o resto vira `movies` (`RelationType.MOVIE_FORMATS`) ou `specials` (qualquer outro formato), ordenados por `seasonYear`, depois `Season`, depois `anilistId` — um membro sem relação com os outros entra mesmo assim, pelo formato. A mesma classificação é a prévia do detalhe administrativo (`AnimeFranchiseService.getFranchise`).

**Organização administrativa (`AnimeFranchiseService`):** só grava a atribuição (`Anime.franchiseAssignment`/`franchiseRoot`); a franquia resultante sempre sai do `FranchiseResolver`. Cada anime tem uma atribuição só — `AUTO`, `MANUAL_MEMBER`, `MANUAL_ROOT` ou `HIDDEN` —, então ser raiz manual não depende de nenhum outro dado. Criar uma franquia manual põe o anime em `MANUAL_ROOT`; `setAnimeFranchise` não aceita `MANUAL_ROOT`. Mover uma raiz manual pra outra franquia (`MANUAL_MEMBER`) passa os membros `MANUAL_MEMBER` dela pra nova raiz (`Anime.moveManualMembers`); uma raiz natural junta pela própria resolução. Uma raiz manual que volta pra `AUTO` ou é oculta deixa de ser raiz e os membros dela a acompanham; remover a franquia (`deleteFranchise`) volta a raiz e os membros pra `AUTO`. A resposta é a franquia de destino (`MANUAL_MEMBER`) ou a de origem (`AUTO`/`HIDDEN`); se a de origem deixou de existir, a atual do anime; se ele não está em nenhuma, vem sem corpo.

**Resolução de overrides (`AnimeOverrideService`):** usada pelo próprio `toResponse`/`toBindingSummary` dele (chamados por `AnimeService`, `AnimeRelationsService` e `AnimeFranchiseService`) e pelos nós do grafo em `AnimeRelationsService` pra decidir, campo a campo, entre o override manual (TMDB) e o dado cru (AniList); banner e logo só existem via override (a AniList não tem esses campos).

**Bind TMDB (`AnimeTmdbService.bindTmdb`):** valida que o anime existe e ainda não tem binding → confirma o `tmdbId` no TMDB pro `mediaType` informado → grava `tmdbId`, `tmdbMediaType` e `tmdbBoundAt`. Não mexe nos overrides de imagem.

**Preenchimento dos overrides (`AnimeTmdbOverridesFillService.fillOverrides`):** lê traduções e imagens do título vinculado (cache-first) e grava, devolvendo num `TmdbOverridesFill` o que gravou e os campos sem opção:

Cada idioma é procurado do mais específico ao mais amplo — idioma e país (`pt-BR`), depois só o idioma (`pt`, de qualquer país, inclusive `pt-PT`) —, primeiro o idioma configurado e depois o de fallback (`tmdb.override.fallback-language`, seed `en`). As settings de idioma aceitam `pt` ou `pt-BR`.

- título e sinopse — setting de idioma (`tmdb.override.title-language`/`tmdb.override.synopsis-language`) vazia grava `null`, mantendo o dado da AniList; preenchida, usa a primeira tradução com texto nessa ordem;
- logo — usa só `tmdb.image.logo-language`, sem o idioma de fallback: uma lista de idiomas separados por vírgula, em ordem de preferência (ex.: `en-US,ja,none`), com `none` pro logo sem idioma. Fica o mais votado do primeiro idioma da lista que tiver logo, cada um procurado do mais específico ao mais amplo; lista vazia grava `null`. A lista existe porque a maioria dos títulos do TMDB não tem logo no idioma preferido nem sem idioma, mas tem em japonês;
- backdrop — só se o anime ainda não tem um: o mais votado sem idioma, ou o mais votado na ordem do idioma de fallback. Os demais campos são sobrescritos;
- campo sem opção nem no idioma configurado nem no de fallback é gravado `null`; fallback vazio desliga o fallback.

Preferência de idioma com valor fora do formato (`pt_BR`, `en US`) é recusada pelo `SettingService` com `BusinessRuleException`, em vez de virar um idioma vazio que não encontra nada. Traduções e imagens são buscadas fora de transação; só a escolha e a gravação rodam numa transação curta (`writeOverrides`).

## Arquivos críticos

- `model/Anime.java` — entidade central; PK é o próprio `anilistId` (sem id surrogate), concentra dados AniList + binding/overrides TMDB + atribuição de franquia (`franchiseAssignment`, `franchiseRoot`) + revisão.
- `service/AnimeOverrideService.java` — motor único de resolução override↔dado cru, reaproveitado pelas outras facetas.
- `service/AnimeService.java` — binding↔pasta, sync, revisão; ponto de entrada mais usado por outros domínios.
- `service/AnimeRelationsService.java` — sync recursivo do grafo e montagem das respostas de grafo, raízes e franquia (`getAnimeRelationGraph`, `listRelationRoots`, `getAnimeFranchise`).
- `service/AnimeFranchiseService.java` — organização administrativa das franquias.
- `graph/FranchiseResolver.java` + `graph/model/FranchiseResolution.java` — resolução da franquia de cada anime (pai automático, atribuição, raízes manuais, ciclos); lógica mais complexa do domínio. Sem CDI, como o `RelationGraph`: só `resolve()` e `resolveAgain()` são públicos e estáticos; a instância é privada, só pra guardar o estado da resolução em andamento.
- `graph/FranchiseClassifier.java` — divisão dos membros em temporadas/filmes/especiais.
- `graph/RelationGraph.java` — percursos sobre o grafo de relações: fechamento por BFS (`relations/graph`) e cadeia de `SEQUEL` (temporadas da franquia). Classe estática sem CDI, não `*Service`, porque não orquestra regra de negócio nem tem resource — só lê `AnimeRelation` pelas consultas da entidade, dentro da transação de quem chama.
- `graph/model/RelationClosure.java` — resultado da BFS (`animeIds` alcançados + `edges` percorridas).
- `service/AnimeTmdbOverridesFillService.java` — escolha por idioma dos overrides preenchidos a partir do TMDB.
- `service/EpisodePairingResolutionService.java` e `pairing/` — resolução do pairing em etapas (numeração da pasta, conversão pelo anibridge com a numeração absoluta, validação no TMDB); `pairing/FolderEpisodeNumbering.java` decide o número de cada arquivo quando o extrator falha na pasta, e um erro ali pareia a pasta inteira errado.
- `service/AnimeTmdbFilePairingFillService.java` — o que o pairing automático grava sozinho: mantém pairing existente e não grava episódio em conflito.
- `service/AnimeTmdbService.java` — binding TMDB, overrides, schema de opções.
- `service/AnimeTmdbFilePairingService.java` — pairing por arquivo e schema de temporadas/episódios.
- `model/AnimeRelation.java` + `model/RelationType.java` — grafo de relações; `RelationType` concentra as listas-fonte-única usadas pela franquia (`DERIVED_LINKS`, `AUTO_PARENT_LINKS`, `SERIES_FORMATS`, `MOVIE_FORMATS`).
- `model/Season.java` — enum da estação de estreia (`WINTER`/`SPRING`/`SUMMER`/`FALL`), usado só pra ordenar `movies`/`specials` da franquia sem uma data de estreia mais precisa persistida localmente.

## Observações técnicas e débitos identificados

- **Uso inconsistente de `RetryUtils`:** dentro do domínio, só têm retry de rate limit a busca de episódios das temporadas nas duas estratégias de pairing (`TmdbEpisodeValidator`, `EpisodeContinuityResolutionService`), envolvendo `TmdbService.getSeasonEpisodes` em `RetryUtils.withRateLimitRetry` (cada tentativa passa pelo proxy do `TmdbService` com a transação dela, então a espera do retry não segura conexão). As demais chamadas de rede — `anilistSearchService.findById` (bind e sync pelo portal, cascata de `syncRelations`, proposta de binding), `tmdbService.getTvDetails/getMovieDetails` (bind e estado TMDB, schemas, temporadas da continuidade), traduções/imagens do schema de overrides e `tmdbService.getSeason` (file-pairing schema) — não têm retry nem tratamento de rate limit. Sob rate limiting da AniList/TMDB, esses caminhos falham direto. **Backlog real** (confirmado pelo mantenedor, mesmo item do risco geral de retry ad hoc).
- **`syncRelations` roda recursão não limitada dentro de uma única `@Transactional`:** cada vizinho ainda não visitado dispara uma nova chamada de rede à AniList antes de continuar a recursão. Em franquias grandes, isso significa N chamadas de rede sequenciais dentro de uma transação de banco só, sem timeout ou circuit breaker visível — risco de transação longa/timeout proporcional ao tamanho da franquia. **Backlog real** (confirmado pelo mantenedor).
- **`getTmdbFilePairingSchema` faz uma chamada por temporada** (`tmdbService.getSeason`), sequencial, um N+1 clássico sobre a API do TMDB — **confirmado**: `getSeason` é cache-first via `IntegrationCache` (mesmo padrão dos demais métodos de `TmdbService`), então chamadas repetidas pela mesma temporada não batem de novo no upstream; o N+1 em si (N chamadas sequenciais na primeira vez) continua existindo, mas o custo de rede real só ocorre uma vez por temporada por TTL.
- **`clearTmdbBinding` não toca em `TmdbFilePairing`:** ao remover o binding TMDB, os overrides do anime são zerados, mas os pairings de episódio dos vídeos da pasta (nome, sinopse, thumbnail vindos do TMDB) permanecem intactos, agora desvinculados de qualquer binding TMDB ativo. **Lacuna a cobrir** (confirmado pelo mantenedor): o comportamento correto é limpar/invalidar os pairings junto ao remover o binding; vai pro backlog.
- **Sem paginação em lookups batch:** `AnimeService.getAnimeBindings` e `AnimeTmdbFilePairingService.getTmdbFilePairings` recebem a lista inteira de ids do chamador (`mediafolder`/`videofile`) sem limite — hoje acompanha o tamanho da página do chamador, mas não há um teto próprio caso isso mude. **Backlog real** (confirmado pelo mantenedor).
- **Custo da resolução de franquias:** `FranchiseResolver.resolve()` lê a coleção inteira (duas consultas) a cada chamada, inclusive pra preencher o `franchiseRootId` de um único `AnimeResponse` (`AnimeService.toResponse`), e as listagens administrativas (`listFranchises`, `listHiddenAnimes`) filtram em memória antes de paginar no banco. Com a coleção atual (~900 animes) o custo é baixo; cresce linearmente com ela.
- Nenhum `TODO`/`FIXME`/`XXX` encontrado no domínio.
- Tratamento de concorrência no bind TMDB/AniList: não há lock explícito nem verificação de condição de corrida entre a checagem de duplicidade (`Anime.find`/`anime.tmdbId != null`) e o `persist()`/commit — duas requisições simultâneas pro mesmo `anilistId` podem ambas passar a validação antes de uma persistir (mitigado só pela constraint de PK no banco, que faria a segunda falhar com exceção não tratada especificamente aqui). **Backlog real** (confirmado pelo mantenedor, mesmo item do risco geral de corrida no bind).
- Boa prática observada: leituras usadas fora do contexto de request HTTP (`findByFolder` e `find` em `AnimeService`) foram corretamente marcadas `@Transactional` mesmo sendo apenas leitura, pra funcionar também chamadas fora de uma transação ambiente — mostra atenção deliberada a esse detalhe, não um padrão acidental.
