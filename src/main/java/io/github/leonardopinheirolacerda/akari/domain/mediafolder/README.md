## Objetivo do módulo

Pastas do catalog ingeridas de storage providers (ex.: MEGA), representando a estrutura em
árvore replicada no banco (`media_folder`). Serve a API `/v1/catalog/folders` (tag
`MediaFolder` do contrato) e também é usado internamente pela ingestão para persistir a
árvore vinda do provider. O catalog é agnóstico a metadata — enriquecimento
(episódio, temporada, thumbnail etc.) fica em módulos de metadata donos do binding
`folderId → payload`.

## Responsabilidade principal

CRUD administrado das pastas via API (create/update/delete/get/list) e suporte estrutural
usado pela ingestão: busca ou criação por `path` com resolução do pai, busca de subtree por
provider e remoção em lote por raízes de um conjunto de pastas.

## Funcionalidades existentes

- `POST/GET/PUT/DELETE /v1/catalog/folders` (CRUD completo).
- Listagem paginada com filtros em AND (`name` substring case-insensitive, `mediaType`,
  `storageProviderId`, `parentId`, `hasFiles`, `reviewStatus`, `hasAnilist`, `hasTmdb`,
  `allEpisodesPaired`) e `sort` com whitelist `name`/`createdAt`.
- Resolução automática do pai a partir do `path` no create/update — se o pai não estiver
  registrado, a pasta vira raiz (`parentId` nulo), não é erro.
- Enriquecimento cross-domain na listagem: cada pasta da página recebe `animeBinding` (via
  `AnimeService.getAnimeBindings`), evitando round-trip extra do client.
- Suporte à ingestão: `findOrCreate()` (devolve a pasta do path ou cria, resolvendo o pai) e
  `findSubtree()` por provider/prefixo de path.

## Dependências internas e externas

- `storageprovider` (model `StorageProvider`, service `StorageProviderService`) — toda pasta
  pertence a um provider; `findOrThrow` usado em `createFolder`/`updateFolder`.
- `videofile` (model `VideoFile`) — associação `@OneToMany` usada pelo filtro `hasFiles` da
  listagem (`size(videoFiles)` no JPQL) e pelo campo `hasFiles` da resposta
  (`MediaFolder.hasFiles()`, chamado pelo `MediaFolderMapper`), que carrega a coleção.
- `anime` (service `AnimeService`) — `getAnimeBindings` para enriquecer a listagem.
- `anime`/`videofile` por **nome de entidade em HQL**, não por import Java — os filtros
  `reviewStatus`/`hasAnilist`/`hasTmdb`/`allEpisodesPaired` de `findPage` usam subquery
  correlacionada (`EXISTS`/`IN`) referenciando `Anime`, `TmdbFilePairing` e `VideoFile` só
  como texto na query; `MediaFolder.java` não importa nada desses domínios, preservando o
  sentido único da dependência de pacote (`anime` → `mediafolder`, nunca o contrário).
- `utils.DirectoryUtils` (`parentOf`) — deriva o path do pai a partir do path da pasta.
- `utils.PaginationUtils` — monta o `Sort` do Panache a partir das specs de `sort`.
- `exceptions.ResourceNotFoundException`, `exceptions.BusinessRuleException`.
- DTOs gerados: `MediaFolderRequest`/`Response`/`Page`, `MediaType`, `AnimeBindingSummary`.

## Módulos relacionados

- `ingestion` (`IngestionService`, `IngestionTaskService`) — maior consumidor externo; chama
  `findOrCreate()`, `findOrThrow()` e `findSubtree()` diretamente, fora do fluxo HTTP. `ingestion_task.folder_id` referencia `media_folder` com `ON DELETE CASCADE`: remover a
  pasta remove as tasks dela.
- `videofile` — todo `VideoFile` referencia uma `MediaFolder` (FK obrigatória).
- `anime` — bindings de anime por pasta, usados no enriquecimento da listagem.
- `storageprovider` — owner de cada pasta.

## Pontos de entrada

- `MediaFolderResource` (implementa `MediaFolderApi` gerado) — camada HTTP, delega 100% ao
  service.
- `MediaFolderService` — também chamado diretamente por serviços de ingestão (`findOrCreate`,
  `findOrThrow`, `findSubtree`), fora da camada HTTP.

## Fluxos importantes

- `createFolder`: valida `storageProviderId` existente → valida duplicidade (mesmo provider +
  path) → resolve `parent` pelo path do pai → persiste.
- `updateFolder`: valida duplicidade considerando que outra pasta (não a própria) já tenha o
  novo path → revalida `storageProviderId` → redescobre `parent`; `createdAt` é preservado por
  ser `updatable = false`.
- `findOrCreate` (chamado pela ingestão): se já existe pasta com o `path` no provider, devolve
  como está (sem atualizar `name`, `mediaType` nem `parent`) → senão valida `storageProviderId`
  → resolve `parent` pelo path do pai → persiste. O pai só é encontrado se já estiver commitado,
  por isso a ingestão chama em sequência, da pasta mais rasa pra mais funda.
- `listFolders`: pagina → mapeia a página para DTO → busca bindings de anime em lote pelos
  `folderId`s da página → seta `animeBinding` em cada item (`null` quando não vinculado).
- `deleteFolder`: remove a pasta; a cascata de DDL remove descendentes, arquivos abrigados e
  as `IngestionTask`s das pastas.

## Arquivos críticos

- `model/MediaFolder.java` — queries estáticas (`find`, `findByProviderAndPath`,
  `findSubtree`, `findPage`) e a modelagem da árvore via `parent`/`path`.
- `service/MediaFolderService.java` — CRUD, `findOrCreate()` usado pela ingestão e o
  enriquecimento da listagem com os bindings de anime.

## Observações técnicas e débitos identificados

- `findOrCreate()` (usado pela ingestão) checa a existência antes de criar, mas não há
  constraint de unicidade em `(storage_provider_id, path)`: duas chamadas concorrentes pro mesmo
  path (ex.: duas ingestões simultâneas sobre o mesmo subtree) podem criar pastas duplicadas sem
  erro.
- O campo `hasFiles` da resposta vem de `MediaFolder.hasFiles()`, que carrega a coleção
  `videoFiles` inteira: na listagem paginada, cada pasta da página carrega todos os seus
  arquivos só pra saber se tem algum (uma consulta por pasta).
- O filtro `hasFiles` em `findPage` usa `size(videoFiles) > 0`/`= 0` em JPQL — potencialmente
  custoso em pastas com muitos arquivos; não há índice dedicado visível no código para essa
  contagem. **Aceito** (confirmado pelo mantenedor): escala de uso pessoal/doméstico não
  justifica otimizar agora.
- Os filtros `reviewStatus`/`hasAnilist`/`hasTmdb`/`allEpisodesPaired` referenciam `Anime`/
  `TmdbFilePairing`/`VideoFile` só pelo nome na string HQL (sem import) — evita acoplamento de
  pacote em `mediafolder`, mas é um acoplamento de schema sem proteção do compilador: renomear
  um campo usado aqui (ex. `Anime.tmdbId`, `Anime.reviewStatus`) em `anime` quebra essa query em
  runtime, não em build. `allEpisodesPaired`, especificamente, nunca bate (nem `true` nem
  `false`) numa pasta sem nenhum arquivo — não é um terceiro valor, é a ausência de dado pra
  avaliar a condição.
- `MediaFolder.findPage` usa `FROM MediaFolder m WHERE ...` com alias explícito (em vez do
  curto `where ...` sem `FROM`) desde que os filtros de anime precisaram de subquery
  correlacionada — sem alias, a correlação do `EXISTS`/`IN` com a pasta externa seria ambígua.
- **Confirmado nas migrations:** `media_folder.parent_id` e `video_file.folder_id` têm
  `ON DELETE CASCADE` na DDL (`V2__create-catalog-tables.sql`) — a cascata de descendentes e
  arquivos ao deletar uma pasta é real. Já `anime.folder_id` (`V6__create-anime-table.sql`)
  **não tem** cascade — remover uma pasta com anime vinculado (`deleteFolder`) falha por
  violação de FK; o anime precisa ser removido antes.
