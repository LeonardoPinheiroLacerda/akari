## Objetivo do módulo

Arquivos de vídeo do catalog, sempre abrigados por uma `MediaFolder`. Serve a API
`/v1/catalog/video-files` (tag `VideoFile` do contrato: "arquivos de vídeo dentro de uma
pasta"). É sobre essa entidade que os domínios `cache` e `mediaprobe` resolvem onde o arquivo
está disponível em disco, segundo o javadoc da própria entidade.

## Responsabilidade principal

CRUD administrado das pastas via API e suporte à ingestão: listagem sem paginação do conjunto
inteiro de arquivos de uma pasta, usada na reconciliação e no pairing automático.

## Funcionalidades existentes

- `POST/GET/PUT/DELETE /v1/catalog/video-files` (CRUD completo).
- `path` derivado server-side (`folder.path` + `name`) tanto em `createVideoFile` quanto em
  `updateVideoFile`.
- Listagem paginada por `folderId`: devolve página vazia (não 404) se a pasta não existir ou
  não tiver arquivos.
- Enriquecimento cross-domain na listagem: cada arquivo da página recebe `tmdbPairing` (via
  `AnimeTmdbFilePairingService.getTmdbFilePairings`).
- `listByFolder`: lista completa sem paginação, usada pela ingestão para reconciliação e
  pairing automático, a partir de contextos sem transação ambiente.

## Dependências internas e externas

- `mediafolder` (model `MediaFolder`, service `MediaFolderService`) — FK obrigatória `folder`;
  `findOrThrow` usado em `createVideoFile`/`updateVideoFile`.
- `anime` (service `AnimeTmdbFilePairingService`) — pairing TMDB por arquivo, usado no
  enriquecimento da listagem.
- `utils.DirectoryUtils` (`join`) — monta `path = folder.path + name`.
- `exceptions.ResourceNotFoundException`.

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
- `ingestion` (`FolderAutoBindService`, `ReconciliationService`) — reconciliação e pairing
  automático via `listByFolder`.
- `anime` (`AnimeTmdbFilePairingService`) — pairing TMDB consumido na listagem.
- `utils.VideoFileLocationUtils` — resolve a localização em disco (direto se `LOCAL`, via
  `VideoFileCache` se remoto) a partir do `VideoFile` e do seu `StorageProvider`.

## Pontos de entrada

- `VideoFileResource` (implementa `VideoFileApi` gerado) — camada HTTP, delega 100% ao
  service.
- `VideoFileService` — `listByFolder` é chamado diretamente por serviços de ingestão, fora da
  camada HTTP.

## Fluxos importantes

- `createVideoFile`: valida `folderId` existente → deriva `path` via
  `DirectoryUtils.join(folder.path, name)` → persiste.
- `updateVideoFile`: revalida `fileId` e o novo `folderId` → rederiva `path` com a pasta
  (possivelmente nova).
- `listVideoFiles`: pagina por `folderId` → mapeia a página para DTO → busca pairings TMDB em
  lote pelos `fileId`s da página → seta `tmdbPairing` em cada item (`null` quando não
  pareado).
- `listByFolder`: leitura sem paginação, anotada `@Transactional` mesmo sendo leitura — segundo
  o javadoc do método, porque é chamada de contextos sem transação ambiente (ingestão).

## Arquivos críticos

- `model/VideoFile.java` — queries estáticas (`find`, `findByFolderId`, `findPage`).
- `service/VideoFileService.java` — `createVideoFile`/`updateVideoFile` (derivação de `path`)
  e `listVideoFiles` (enriquecimento com `tmdbPairing`).

## Observações técnicas e débitos identificados

- `listVideoFiles` devolve página vazia (não 404) quando a pasta não existe — comportamento
  documentado explicitamente no javadoc do resource; ainda assim pode mascarar um `folderId`
  inválido vindo do client, que não recebe nenhum sinal de erro. **Backlog real** (confirmado
  pelo mantenedor, mesma categoria do risco equivalente em `mediafolder`).
- Nenhuma validação de unicidade de `path`/`name` dentro da mesma pasta foi encontrada no
  código (diferente de `MediaFolder`, que valida duplicidade de provider+path) — dois arquivos
  com o mesmo `name` na mesma pasta não são impedidos pelo service. **Backlog real** (confirmado
  pelo mantenedor).
