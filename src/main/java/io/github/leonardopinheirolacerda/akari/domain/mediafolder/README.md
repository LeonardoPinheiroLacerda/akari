## Objetivo do módulo

Pastas do catalog ingeridas de storage providers (ex.: MEGA), representando a estrutura em
árvore replicada no banco (`media_folder`). Serve a API `/v1/catalog/folders` (tag
`MediaFolder` do contrato) e também é usado internamente pela ingestão para persistir e
reconciliar a árvore vinda do provider. O catalog é agnóstico a metadata — enriquecimento
(episódio, temporada, thumbnail etc.) fica em módulos de metadata donos do binding
`folderId → payload`.

## Responsabilidade principal

CRUD administrado das pastas via API (create/update/delete/get/list) e suporte estrutural
usado pela ingestão: resolução de pai por `path`, criação em lote sem validação de
duplicidade (reconciliação), busca de subtree por provider e remoção em lote por raízes de um
conjunto de pastas.

## Funcionalidades existentes

- `POST/GET/PUT/DELETE /v1/catalog/folders` (CRUD completo).
- Listagem paginada com filtros em AND (`name` substring case-insensitive, `mediaType`,
  `storageProviderId`, `parentId`, `hasFiles`) e `sort` com whitelist `name`/`createdAt`.
- Resolução automática do pai a partir do `path` no create/update — se o pai não estiver
  registrado, a pasta vira raiz (`parentId` nulo), não é erro.
- Enriquecimento cross-domain na listagem: cada pasta da página recebe `animeBinding` (via
  `AnimeService.getAnimeBindings`), evitando round-trip extra do client.
- Suporte à ingestão: `create()` sem validação de duplicidade de path, `findSubtree()` por
  provider/prefixo de path, `deleteRoots()` para remoção em lote só das raízes de um conjunto.

## Dependências internas e externas

- `storageprovider` (model `StorageProvider`, service `StorageProviderService`) — toda pasta
  pertence a um provider; `findOrThrow` usado em `createFolder`/`updateFolder`.
- `videofile` (model `VideoFile`) — associação `@OneToMany` só para contagem/filtro
  `hasFiles` (`size(videoFiles)`), sem leitura via cascata.
- `anime` (service `AnimeService`) — `getAnimeBindings` para enriquecer a listagem.
- `utils.DirectoryUtils` (`parentOf`) — deriva o path do pai a partir do path da pasta.
- `utils.PaginationUtils` — monta o `Sort` do Panache a partir das specs de `sort`.
- `exceptions.ResourceNotFoundException`, `exceptions.BusinessRuleException`.
- DTOs gerados: `MediaFolderRequest`/`Response`/`Page`, `MediaType`, `AnimeBindingSummary`.

## Módulos relacionados

- `ingestion` (`FolderRemovalService`, `ReconciliationService`, `IngestionService`) — maior
  consumidor externo; chama `create()`, `findSubtree()`, `deleteRoots()` diretamente, fora do
  fluxo HTTP.
- `videofile` — todo `VideoFile` referencia uma `MediaFolder` (FK obrigatória).
- `anime` — bindings de anime por pasta, usados no enriquecimento da listagem.
- `storageprovider` — owner de cada pasta.

## Pontos de entrada

- `MediaFolderResource` (implementa `MediaFolderApi` gerado) — camada HTTP, delega 100% ao
  service.
- `MediaFolderService` — também chamado diretamente por serviços de ingestão (`create`,
  `findOrThrow`, `findByProviderAndPath`, `findSubtree`, `deleteRoots`), fora da camada HTTP.

## Fluxos importantes

- `createFolder`: valida `storageProviderId` existente → valida duplicidade (mesmo provider +
  path) → resolve `parent` pelo path do pai → persiste.
- `updateFolder`: valida duplicidade considerando que outra pasta (não a própria) já tenha o
  novo path → revalida `storageProviderId` → redescobre `parent`; `createdAt` é preservado por
  ser `updatable = false`.
- `listFolders`: pagina → mapeia a página para DTO → busca bindings de anime em lote pelos
  `folderId`s da página → seta `animeBinding` em cada item (`null` quando não vinculado).
- `deleteRoots` (chamado pela ingestão): `Panache.flush()` explícito antes (garante que
  remoções pendentes de outras entidades referenciadas, ex. `Anime` vinculado, já estejam no
  banco) → identifica as raízes do subconjunto recebido (pastas cujo `parent` não está no
  próprio conjunto) → `delete` em lote por query para cada raiz (evita que o Hibernate valide o
  grafo de entidades no flush e lance `TransientPropertyValueException` por associações
  `@ManyToOne` ainda não resolvidas entre os irmãos carregados na mesma sessão). A cascata de
  DDL remove descendentes e arquivos abrigados.

## Arquivos críticos

- `model/MediaFolder.java` — queries estáticas (`find`, `findByProviderAndPath`,
  `findSubtree`, `findPage`) e a modelagem da árvore via `parent`/`path`.
- `service/MediaFolderService.java` — `deleteRoots()` concentra a lógica mais sensível do
  domínio (flush explícito + delete em lote por query, motivado por um falso positivo do
  Hibernate confirmado empiricamente, segundo o javadoc do método).

## Observações técnicas e débitos identificados

- `create()` (usado pela ingestão) não valida duplicidade de path — assume que quem chama já
  garantiu unicidade; uso incorreto fora do fluxo de reconciliação pode gerar pastas duplicadas
  sem erro. **Backlog real** (confirmado pelo mantenedor).
- `deleteRoots()` depende de todas as pastas do subtree estarem carregadas na mesma sessão
  (vindas de um único `findSubtree`) para calcular corretamente quais são raízes; chamado fora
  desse padrão (ex. com uma lista parcial) pode remover pastas incorretamente ou deixar
  órfãs fora do alcance da cascata.
- O filtro `hasFiles` em `findPage` usa `size(videoFiles) > 0`/`= 0` em JPQL — potencialmente
  custoso em pastas com muitos arquivos; não há índice dedicado visível no código para essa
  contagem. **Aceito** (confirmado pelo mantenedor): escala de uso pessoal/doméstico não
  justifica otimizar agora.
- **Confirmado nas migrations:** `media_folder.parent_id` e `video_file.folder_id` têm
  `ON DELETE CASCADE` na DDL (`V2__create-catalog-tables.sql`) — a cascata de descendentes e
  arquivos ao deletar uma pasta é real. Já `anime.folder_id` (`V6__create-anime-table.sql`)
  **não tem** cascade — por isso `FolderRemovalService.removeFolders` apaga explicitamente o
  `Anime` vinculado a cada pasta antes de chamar `deleteRoots()`; sem esse passo, a remoção
  falharia por violação de FK.
