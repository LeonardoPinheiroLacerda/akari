# ingestion

## Objetivo do módulo

Registrar no catalog as pastas e os arquivos de vídeo de um subtree de storage provider, gravando
cada disparo como uma `Ingestion` com uma `IngestionTask` por pasta, e permitir consultar quais
pastas do provider já foram ingeridas.

## Responsabilidade principal

Orquestrar chamadas a `storageprovider` (listar a árvore remota e os vídeos de cada pasta) e a
`mediafolder`/`videofile` (cadastrar pastas e arquivos), e persistir o estado da ingestão em
quatro tabelas próprias (`ingestion`, `ingestion_task`, `ingestion_step_execution`,
`ingestion_step_event`).

## Funcionalidades existentes

- `POST /v1/ingestion/providers/{providerId}` (operationId `createIngestionTasks`) — cria as
  pastas do subtree, grava a ingestão e dispara em background o cadastro dos vídeos e a task de
  cada pasta; devolve `202 STARTED` (`IngestionResource.createIngestionTasks` →
  `IngestionService.createIngestionTasks`).
- `GET /v1/ingestion/providers/{providerId}/available-folders` — lista a árvore de pastas remotas
  do provider marcando quais já foram ingeridas (`IngestionService.listAvailableFolders`).
- Extração best-effort do número de episódio a partir do nome do arquivo
  (`extractor/EpisodeNumberExtractor`), consumida por `anime` e `videofile`.

## Modelo

- `Ingestion` — um disparo. `status` (`IngestionStatus`) gravado como `PENDING`.
- `IngestionTask` — o processamento de uma pasta dentro da ingestão. `status`
  (`IngestionTaskStatus`) gravado como `PENDING`; `anime` e `currentStep` ficam `null`.
- `IngestionStepExecution` — uma tentativa de uma etapa (`IngestionStep`: `ANILIST_SYNC`,
  `TMDB_BIND`, `TMDB_OVERRIDES`, `TMDB_FILEPAIR`) de uma task, com `attempt` a partir de 1.
- `IngestionStepEvent` — evento da linha do tempo de uma tentativa (`IngestionEventType`), com
  `details` em `JSONB`.

Nenhum fluxo avança os status, executa etapas ou grava `IngestionStepExecution`/
`IngestionStepEvent` hoje: a ingestão termina no cadastro de pastas, arquivos e tasks.

FKs (`V20__create-ingestion-tables.sql`): task → ingestão, execução → task e evento → execução
com `ON DELETE CASCADE`; `ingestion_task.folder_id` com `ON DELETE CASCADE` (a task some com a
pasta); `ingestion_task.anime_id` com `ON DELETE SET NULL` (remover o anime só limpa o vínculo).

## Dependências internas e externas

- `domain/storageprovider` (`StorageProvider`, `StorageProviderService`,
  `StorageProviderClientFactory`, `StorageProviderClient`, `StorageItem`) — lista a árvore remota,
  os vídeos diretos de cada pasta e verifica existência de `rootPath` na listagem de pastas.
- `domain/mediafolder` (`MediaFolder`, `MediaFolderService`) — busca/cria (`findOrCreate`) e
  lista (`findSubtree`) pastas do catalog.
- `domain/videofile` (`VideoFileService`) — lista os arquivos já cadastrados de uma pasta e cria
  os novos.
- `domain/anime` (`Anime`) — `IngestionTask.anime` referencia `Anime`.
- `domain/setting` (`SettingService`) — precheck de `tmdb.api-key` configurado.
- Executor de threads virtuais do Quarkus (`@VirtualThreads ExecutorService`) — roda a criação
  de cada task.
- `exceptions/` (`BusinessRuleException`, `IntegrationException`, `ResourceNotFoundException`).
- `utils/` (`DirectoryUtils`, `FileNameUtils`).

## Módulos relacionados

`domain/videofile` (`VideoFileService`, pra calcular `naturalSortKey`) e `domain/anime`
(`EpisodePairingResolutionService`, na resolução do pairing) importam
`extractor/EpisodeNumberExtractor` — as únicas classes fora de `domain/ingestion` que importam
algo daqui. Fora essa exceção, o módulo é um orquestrador de ponta: consome vários domínios, não
é consumido por nenhum outro.

## Pontos de entrada

- `resource/IngestionResource` — implementa `IngestionApi` (gerado do contrato), único ponto de
  entrada HTTP do domínio.
- `service/IngestionService` — ponto de entrada lógico do disparo e da listagem.
- `service/IngestionTaskService` — chamado só pelo `IngestionService`, dentro das threads
  virtuais.

## Fluxos importantes

**Disparo** (`IngestionService.createIngestionTasks`), em duas fases:

1. Síncrona, na thread da requisição: valida TMDB configurado (`400`) e provider existente
   (`404`) → lista a árvore remota a partir de `rootPath` (`null` vira a raiz do provider) e
   acrescenta o próprio `rootPath` como pasta (`StorageItem.withSyntheticRoot` — o `listTree` só
   devolve descendentes) → ordena da pasta mais rasa pra mais funda (`StorageItem.sortedByDepth`)
   → persiste a `Ingestion` como `PENDING` (transação própria) → pra cada pasta, nessa ordem,
   `MediaFolderService.findOrCreate` (transação própria) e lista os vídeos diretos dela.
2. Paralela, uma thread virtual por pasta: `IngestionTaskService.createIngestionTask` cadastra os
   vídeos novos e cria a `IngestionTask` como `PENDING`, numa transação só. Toda pasta ganha
   task, inclusive as sem vídeos diretos.

A resposta `202` sai depois de submeter as tasks, sem esperar por elas. A criação das pastas fica
na fase sequencial, não nas tasks, porque o `findOrCreate` resolve o `parent` buscando o pai pelo
path: com pastas criadas em paralelo, o filho não acharia o pai ainda não commitado e ficaria
sem pai.

**Cadastro dos vídeos** (`IngestionTaskService.createIngestionTask`): lê os nomes já cadastrados
na pasta (`VideoFileService.listByFolder`) e só chama `createVideoFile` pros novos. A checagem é
feita antes, e não capturando o `ConflictException` do `createVideoFile`: a exceção, mesmo
capturada, marca a transação da task pra rollback e a task inteira se perderia no commit. Falha
de uma task fica só no log (`Falha ao criar a tarefa de ingestão da pasta ...`), sem afetar as
demais.

## Arquivos críticos

- `service/IngestionService.java` — disparo em duas fases; a ordem e a transação por pasta da
  fase sequencial garantem o `parent` das pastas.
- `service/IngestionTaskService.java` — cadastro dos vídeos e da task; a checagem de arquivo
  existente antes de criar evita o rollback silencioso da task.
- `extractor/EpisodeNumberExtractor.java` — heurística de regex ajustada empiricamente contra ~10
  mil arquivos reais; qualquer alteração de pattern tem risco real de regressão silenciosa no
  pairing e na ordem da listagem de episódios.
- `src/main/resources/db/V20__create-ingestion-tables.sql` — tabelas e FKs do domínio.

## Observações técnicas e débitos identificados

- Sem deduplicação de execuções: dois disparos simultâneos sobre o mesmo subtree rodam juntos.
  Como não há constraint de unicidade em `media_folder (storage_provider_id, path)` nem em
  `video_file (folder_id, path)`, a checagem-antes-de-criar de pastas e arquivos não impede
  duplicatas nesse cenário.
- A listagem da árvore, a listagem dos vídeos de cada pasta (uma chamada ao provider por pasta) e
  a criação das pastas rodam na thread da requisição: em árvores grandes num provider remoto, a
  resposta demora.
- Falha na listagem do provider no disparo sobe sem tradução (`500`), diferente de
  `listAvailableFolders`, que a embrulha em `IntegrationException` (`502`).
- `rootPath` não é validado contra o provider no disparo. Com um provider que devolve lista
  vazia pra diretório inexistente (`LocalStorageProviderClient`), a pasta do `rootPath` é criada
  no catalog mesmo assim.
- As tasks rodam sem limite de paralelismo — uma thread virtual por pasta, cada uma com sua
  transação, disputando o pool de conexões do datasource.
- Pastas e arquivos que sumiram do provider não são removidos do catalog.
- Pasta já existente é reaproveitada como está pelo `findOrCreate`: `mediaType` e `parent` não são
  atualizados, então uma pasta gravada sem pai continua sem pai numa nova ingestão.
- `IngestionResource` não tem `@ApplicationScoped` explícito na classe (diferente de
  `CacheResource`); sem anotação de escopo, o CDI trata a classe como `@Dependent` — o JAX-RS
  resource funciona normalmente, só sem o pooling de `@ApplicationScoped`. **Backlog real**
  (confirmado pelo mantenedor).
