## Objetivo do módulo

Arquivos de vídeo do catalog, sempre abrigados por uma `MediaFolder`. Serve a API
`/v1/catalog/video-files` (tag `VideoFile` do contrato: "arquivos de vídeo dentro de uma
pasta"). É sobre essa entidade que os domínios `cache` e `mediaprobe` resolvem onde o arquivo
está disponível em disco, segundo o javadoc da própria entidade.

## Responsabilidade principal

CRUD administrado dos arquivos via API e suporte à ingestão: listagem sem paginação do
conjunto inteiro de arquivos de uma pasta, usada pra saber quais arquivos já estão cadastrados.

## Funcionalidades existentes

- `POST/GET/PUT/DELETE /v1/catalog/video-files` (CRUD completo).
- `path` derivado server-side (`folder.path` + `name`) tanto em `createVideoFile` quanto em
  `updateVideoFile`; as duas operações também (re)calculam `naturalSortKey` a partir do
  `name`. `createVideoFile` recusa com `409` um arquivo cujo `path` já existe na pasta.
- Listagem paginada por `folderId` (`listVideoFiles`): devolve página vazia (não 404) se a
  pasta não existir ou não tiver arquivos. Ordem não é garantida — não é a ordem usada pela
  tela de episódios.
- Listagem paginada em ordem natural de nome (`listVideoFilesOrderedByNaturalSortKey`): mesma
  página vazia nos mesmos casos, mas ordenada por `naturalSortKey` (número de episódio
  extraído do nome, zero-preenchido; nome em minúsculas quando não extrai) com `id` como
  desempate — base da listagem de episódios do anime (`AnimeEpisodeService`). Arquivo sem
  `naturalSortKey` (ingerido antes dessa coluna existir) vai para o fim.
- Enriquecimento cross-domain em ambas as listagens: cada arquivo da página recebe
  `tmdbPairing` (via `AnimeTmdbFilePairingService.getTmdbFilePairings`).
- `listByFolder`: lista completa sem paginação, usada pela ingestão (`IngestionTaskService`)
  pra pular os arquivos que a pasta já tem, a partir de contextos sem transação ambiente.
- `listByFolderOrderedByNaturalSortKey`: lista completa sem paginação, na mesma ordem natural da
  listagem de episódios (`naturalSortKey NULLS LAST`, depois `id`); usada pelas resoluções de
  pairing do `anime` (anibridge e continuidade).

## Dependências internas e externas

- `mediafolder` (model `MediaFolder`, service `MediaFolderService`) — FK obrigatória `folder`;
  `findOrThrow` usado em `createVideoFile`/`updateVideoFile`.
- `anime` (service `AnimeTmdbFilePairingService`) — pairing TMDB por arquivo, usado no
  enriquecimento das duas listagens; `AnimeEpisodeService` (mesmo domínio `anime`) consome
  `listVideoFilesOrderedByNaturalSortKey` pra montar a tela de episódios.
- `ingestion` (`extractor.EpisodeNumberExtractor`) — mesma heurística usada pela resolução do
  pairing de episódios, reaproveitada pra extrair o número de episódio que compõe
  `naturalSortKey`. Dependência de mão dupla entre os dois domínios: `ingestion` consome
  `videofile` (`VideoFileService` ao cadastrar os arquivos) e é consumido por ele, só por esse
  extractor — deliberado, pra não duplicar a extração de episódio em dois lugares.
- `utils.DirectoryUtils` (`join`) — monta `path = folder.path + name`.
- `exceptions.ResourceNotFoundException`, `exceptions.ConflictException`.

## Módulos relacionados

- `mediafolder` — todo `VideoFile` pertence a uma pasta (relação inversa da pasta é só para
  contagem/filtro, ver README de `mediafolder`).
- `cache` — `VideoFileCache` guarda um `@ManyToOne VideoFile videoFile` (FK direta); `CacheService`
  resolve a entidade com `VideoFile.find(fileId)` ao enfileirar um download.
- `mediaprobe` (`MediaProbeService`) — resolve a entidade com `videoFileService.findOrThrow(fileId)`
  antes de rodar o probe.
- `playback` — `PlaybackSession` guarda um `@ManyToOne VideoFile videoFile`; `PlaybackService`
  resolve com `videoFileService.findOrThrow(...)` ao iniciar sessão e ao reconciliar.
  Em todos os três casos o acoplamento é raso: busca pontual por id, sem leitura de campos além
  dos expostos pelo model — confirmado lendo os três services, não só por grep de import.
- `ingestion` (`IngestionTaskService`) — lê os arquivos já cadastrados via `listByFolder` e
  cadastra os novos via `createVideoFile`.
- `anime` (`AnimeTmdbFilePairingService`, `AnimeEpisodeService`, `EpisodePairingResolutionService`,
  `EpisodeContinuityResolutionService`) — pairing TMDB consumido nas listagens;
  `AnimeEpisodeService` consome `listVideoFilesOrderedByNaturalSortKey` direto; as duas
  resoluções de pairing da proposta do portal (anibridge e continuidade) listam os arquivos da
  pasta via `listByFolderOrderedByNaturalSortKey`, sem paginação e na mesma ordem natural da
  listagem de episódios.
- `utils.VideoFileLocationUtils` — resolve a localização em disco (direto se `LOCAL`, via
  `VideoFileCache` se remoto) a partir do `VideoFile` e do seu `StorageProvider`.

## Pontos de entrada

- `VideoFileResource` (implementa `VideoFileApi` gerado) — camada HTTP, delega 100% ao
  service.
- `VideoFileService` — `listByFolder` e `createVideoFile` são chamados diretamente pela
  ingestão, fora da camada HTTP; `listVideoFilesOrderedByNaturalSortKey` é chamado por
  `AnimeEpisodeService` (sem resource próprio nesse domínio).

## Fluxos importantes

- `createVideoFile`: valida `folderId` existente → deriva `path` via
  `DirectoryUtils.join(folder.path, name)` → lança `ConflictException` (409) se a pasta já tem
  arquivo com esse `path` → calcula `naturalSortKey` via
  `naturalSortKeyOf(name)` (privado; extrai o episódio com `EpisodeNumberExtractor`, zero
  preenche; sem extração, cai pro nome em minúsculas) → persiste.
- `updateVideoFile`: revalida `fileId` e o novo `folderId` → rederiva `path` com a pasta
  (possivelmente nova) → recalcula `naturalSortKey`.
- `listVideoFiles`/`listVideoFilesOrderedByNaturalSortKey`: paginam por `folderId` (a segunda,
  em ordem natural de nome) → mapeiam a página para DTO → buscam pairings TMDB em lote pelos
  `fileId`s da página → setam `tmdbPairing` em cada item (`null` quando não pareado) — mesmo
  helper privado (`toPageWithPairings`) nos dois casos.
- `listByFolder`/`listByFolderOrderedByNaturalSortKey`: leitura sem paginação, anotadas
  `@Transactional` mesmo sendo leitura, pra funcionar também chamadas de contextos sem
  transação ambiente (ex.: as threads virtuais da ingestão).

## Arquivos críticos

- `model/VideoFile.java` — queries estáticas (`find`, `findByFolderAndPath`, `findByFolderId`,
  `findPage`, `findPageOrderedByNaturalSortKey`).
- `service/VideoFileService.java` — `createVideoFile`/`updateVideoFile` (derivação de `path` e
  `naturalSortKey`) e as duas listagens paginadas.

## Observações técnicas e débitos identificados

- `listVideoFiles` devolve página vazia (não 404) quando a pasta não existe — comportamento
  documentado explicitamente no javadoc do resource; ainda assim pode mascarar um `folderId`
  inválido vindo do client, que não recebe nenhum sinal de erro. **Backlog real** (confirmado
  pelo mantenedor, mesma categoria do risco equivalente em `mediafolder`).
- A unicidade de `path` dentro da mesma pasta só é validada em `createVideoFile` (checagem
  antes de inserir, sem constraint no banco): `updateVideoFile` não valida, e duas criações
  concorrentes do mesmo arquivo passam as duas. **Backlog real** (confirmado pelo mantenedor).
