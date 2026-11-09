# ingestion

## Objetivo do módulo

Registrar no catalog as pastas e os arquivos de vídeo de um subtree de storage provider, gravando
cada disparo como uma `Ingestion` com uma `IngestionTask` por pasta com vídeo, executar as tasks
step a step e permitir consultar quais pastas do provider já foram ingeridas.

## Responsabilidade principal

Orquestrar chamadas a `storageprovider` (listar a árvore remota e os vídeos de cada pasta) e a
`mediafolder`/`videofile` (cadastrar pastas e arquivos), executar os steps de cada task contra
`anilist`/`anime`, e persistir o estado da ingestão em quatro tabelas próprias (`ingestion`,
`ingestion_task`, `ingestion_step_execution`, `ingestion_step_event`).

## Funcionalidades existentes

- `POST /v1/ingestion/providers/{providerId}` (operationId `createIngestionTasks`) — cria as
  pastas do subtree, grava a ingestão e dispara em background o cadastro dos vídeos e a task de
  cada pasta com vídeo; devolve `202 STARTED` (`IngestionResource.createIngestionTasks` →
  `IngestionService.createIngestionTasks`).
- `GET /v1/ingestion/providers/{providerId}/available-folders` — lista a árvore de pastas remotas
  do provider marcando quais já foram ingeridas (`IngestionService.listAvailableFolders`).
- Execução das tasks em background (`IngestionTaskScheduler` → `IngestionTaskExecutionService`):
  uma task por vez, cada uma passando pelos steps em sequência e gravando uma
  `IngestionStepExecution` por step e os `IngestionStepEvent`s dela.
- Step `ANILIST_SYNC` (`AnilistSyncStepService`) — vincula a pasta a um anime da AniList cujo
  nome seja igual ao da pasta.
- Step `TMDB_BIND` (`TmdbBindStepService`) — vincula o anime da pasta à série ou filme do TMDB
  indicado pelo anibridge.
- Step `TMDB_OVERRIDES` (`TmdbOverridesStepService`) — preenche título, sinopse, backdrop e logo
  do anime a partir do TMDB.
- Step `TMDB_FILEPAIR` (`TmdbFilePairStepService`) — pareia os arquivos de vídeo da pasta com os
  episódios da série do TMDB, pelo anibridge.
- Extração best-effort do número de episódio a partir do nome do arquivo
  (`extractor/EpisodeNumberExtractor`), consumida por `anime` e `videofile`, um arquivo por vez
  (`extract`). Também lista os números isolados de um nome (`extractSeparatedNumbers`), que o
  `anime` usa pra numerar a pasta inteira na resolução do pairing (`FolderEpisodeNumbering`).

## Modelo

- `Ingestion` — um disparo. `status` (`IngestionStatus`), `startedAt` (primeira task iniciada) e
  `completedAt` (última task terminada).
- `IngestionTask` — o processamento de uma pasta com vídeo dentro da ingestão. `status`
  (`IngestionTaskStatus`), `currentStep` (último step iniciado) e `anime` (anime vinculado à
  pasta ao fim da task).
- `IngestionStepExecution` — uma tentativa de um step (`IngestionStep`) numa task, com
  `attempt` a partir de 1 e `status` (`IngestionStepStatus`).
- `IngestionStepEvent` — evento da linha do tempo de uma tentativa (`IngestionEventType`), com
  `message` e `details` em `JSONB`.

A task passa por `PENDING` → `RUNNING` → `COMPLETED`/`WAITING_CURATION`/`FAILED`; a execução de
um step nasce `RUNNING` e termina `COMPLETED`, `SKIPPED`, `UNRESOLVED` ou `FAILED`. A
`Ingestion` nasce `PENDING`, passa a `RUNNING` quando a primeira task começa e termina
`COMPLETED` — ou `COMPLETED_WITH_ERRORS`, se alguma task terminou `FAILED` — quando a última
task termina; task em `WAITING_CURATION` conta como terminada, já que a curadoria é um
resultado esperado, não uma falha. Ingestão sem nenhuma task (nenhuma pasta com vídeo) fica
`PENDING`. `FAILED` e `CANCELLED` (ingestão e task) e `PENDING` (execução) não são gravados por
nenhum fluxo.

FKs (`V20__create-ingestion-tables.sql`): task → ingestão, execução → task e evento → execução
com `ON DELETE CASCADE`; `ingestion_task.folder_id` com `ON DELETE CASCADE` (a task some com a
pasta); `ingestion_task.anime_id` com `ON DELETE SET NULL` (remover o anime só limpa o vínculo).

## Dependências internas e externas

- `domain/storageprovider` (`StorageProvider`, `StorageProviderService`,
  `StorageProviderClientFactory`, `StorageProviderClient`, `StorageItem`) — lista a árvore remota,
  os vídeos diretos de cada pasta e verifica existência de `rootPath` na listagem de pastas.
- `domain/mediafolder` (`MediaFolder`, `MediaFolderService`) — busca/cria (`findOrCreate`) e
  lista (`findSubtree`) pastas do catalog.
- `domain/videofile` (`VideoFileService`) — lista (`listByFolder`), conta (`countByFolder`) e
  cria (`createVideoFile`) os arquivos de uma pasta.
- `domain/anime` (`Anime`, `AnimeService`) — o step `ANILIST_SYNC` checa o anime já vinculado à
  pasta (`findByFolder`), se o título escolhido já tem metadata salva (`find`) e vincula
  (`bindAnime`); o step `TMDB_BIND` lê o anime da pasta (`findByFolder`), confere se o título
  escolhido existe no TMDB (`AnimeTmdbService.fetchDetails`) e vincula
  (`AnimeTmdbService.bindTmdb`); o step `TMDB_OVERRIDES` lê o anime da pasta (`findByFolder`)
  e preenche os overrides (`AnimeTmdbOverridesFillService.fillOverrides`); o step
  `TMDB_FILEPAIR` lê o anime da pasta (`findByFolder`) e pareia os arquivos
  (`AnimeTmdbFilePairingFillService.fillPairings`); o fim da task grava
  em `IngestionTask.anime` o anime da pasta
  (`findByFolder`).
- `domain/anilist` (`AnilistSearchService`) — busca dos candidatos pelo nome da pasta.
- `domain/anibridge` (`AnibridgeResolutionService.resolveCandidates`) — melhor candidato TMDB do
  anime no step `TMDB_BIND`, com contagem, suspeita e empate numa busca só.
- `domain/setting` (`SettingService`) — precheck de `tmdb.api-key` configurado.
- Executor de threads virtuais do Quarkus (`@VirtualThreads ExecutorService`) — roda a criação
  de cada task.
- Scheduler do Quarkus (`@Scheduled`) — dispara a execução das tasks.
- `exceptions/` (`BusinessRuleException`, `IntegrationException`, `ResourceNotFoundException`).
- `utils/` (`DirectoryUtils`, `FileNameUtils`, `RetryUtils`).

## Módulos relacionados

`domain/videofile` (`VideoFileService`, pra calcular `naturalSortKey`) e `domain/anime`
(`FolderEpisodeNumbering`, na resolução do pairing) importam
`extractor/EpisodeNumberExtractor` — as únicas classes fora de `domain/ingestion` que importam
algo daqui. Fora essa exceção, o módulo é um orquestrador de ponta: consome vários domínios, não
é consumido por nenhum outro.

## Pontos de entrada

- `resource/IngestionResource` — implementa `IngestionApi` (gerado do contrato), único ponto de
  entrada HTTP do domínio.
- `service/IngestionService` — ponto de entrada lógico do disparo e da listagem.
- `service/IngestionTaskService` — chamado só pelo `IngestionService`, dentro das threads
  virtuais.
- `scheduler/IngestionTaskScheduler` — a cada 1s (`ConcurrentExecution.SKIP`), chama
  `IngestionTaskExecutionService.executeNextTask`.

## Fluxos importantes

**Disparo** (`IngestionService.createIngestionTasks`), em duas fases:

1. Síncrona, na thread da requisição: valida TMDB configurado (`400`) e provider existente
   (`404`) → lista a árvore remota a partir de `rootPath` (`null` vira a raiz do provider) e
   acrescenta o próprio `rootPath` como pasta (`StorageItem.withSyntheticRoot` — o `listTree` só
   devolve descendentes) → ordena da pasta mais rasa pra mais funda (`StorageItem.sortedByDepth`)
   → persiste a `Ingestion` como `PENDING` (transação própria) → pra cada pasta, nessa ordem,
   `MediaFolderService.findOrCreate` (transação própria) e lista os vídeos diretos dela.
2. Paralela, uma thread virtual por pasta: `IngestionTaskService.createIngestionTask` cadastra os
   vídeos novos e cria a `IngestionTask` como `PENDING`, numa transação só. Só pasta com vídeo
   (já cadastrado ou vindo do provider) ganha task.

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

**Execução das tasks** (`IngestionTaskExecutionService.executeNextTask`, a cada 1s): toma a task
`PENDING` ou `RUNNING` mais antiga (`IngestionTask.findNextExecutable`, com a pasta já
carregada) e a marca `RUNNING` (e a ingestão dela, se ainda `PENDING`) → chama os steps em
sequência, pulando os que `isExecuted` diz já terem terminado → um step que termina
`UNRESOLVED` (rodou sem erro, mas não conseguiu decidir sozinho) para a task em
`WAITING_CURATION`, e um que termina `FAILED`, em `FAILED`; se todos passarem, a task fica
`COMPLETED` → `finishTask` grava o status, o `completedAt` e o anime vinculado à pasta e, se era
a última task da ingestão a terminar, fecha a ingestão, na mesma transação. Exceção inesperada
termina a task `FAILED`, só com log.

- **Uma task por vez:** tasks da mesma franquia rodando juntas disputam as arestas de
  `anime_relation` no `syncRelations` do bind (chave duplicada, deadlock), e uma transação não vê
  o anime que a outra ainda não commitou. Com `ConcurrentExecution.SKIP`, o ciclo que cai
  durante uma task em andamento é pulado.
- **Retomada:** uma task `RUNNING` no início de um ciclo só pode ter sobrado de uma queda do
  serviço, já que a anterior sempre termina antes — por isso o scheduler a toma de novo. Os
  steps já terminados (`COMPLETED`/`SKIPPED`) não rodam outra vez; o interrompido roda numa nova
  tentativa (`attempt` + 1).

**Contrato dos steps** (`IngestionStepService`, classe abstrata): cada step estende a classe sem
conhecer os demais nem o orquestrador, e implementa `step()` (qual `IngestionStep` é) e
`execute(task)`, que devolve o `IngestionStepStatus` final. A classe dá o `isExecuted(task)`
padrão (alguma execução do step terminou `COMPLETED` ou `SKIPPED`) e o `start`, que abre a
execução `RUNNING` (com o `attempt` seguinte), marca `currentStep` e devolve um `StepRun`. O
`StepRun` guarda aquela execução e grava os eventos por um método por `IngestionEventType`
(`started`, `requestSent`, `matchSelected`, `error`, `completed`...), cada um com e sem
`details`, e fecha a execução com `finish(status)` — o step não repassa a execução a cada
chamada. É um objeto por execução, não um campo do step service, porque o service é
`@ApplicationScoped` e um campo seria compartilhado entre execuções. Cada gravação é uma
transação curta: a linha do tempo fica visível enquanto o step roda e nenhuma transação fica
aberta durante as chamadas de rede dele.

A `message` do evento aparece no portal: é uma frase pra quem acompanha a ingestão (o que o step
está fazendo, o que encontrou e, quando para à espera de curadoria, o que fazer); códigos, ids e
dados técnicos ficam nos `details`.

**Step `ANILIST_SYNC`** (`AnilistSyncStepService.execute`):

1. `STARTED`. Pasta já vinculada a um anime: `MATCH_SELECTED` + `COMPLETED`, mantém o vínculo.
2. `REQUEST_SENT` → busca o nome da pasta na AniList (5 resultados, com
   `RetryUtils.withRateLimitRetry`) → `RESULTS_FOUND` com um resumo de cada resultado (id,
   títulos, sinônimos, formato, episódios e ano), não a ficha inteira da AniList.
3. Procura um nome igual ao da pasta, sem diferenciar maiúsculas de minúsculas, nesta
   prioridade — cada nível olha todos os resultados antes de passar ao próximo:
   1. título (romaji, inglês, nativo, preferido) exato;
   2. título normalizado;
   3. sinônimo exato;
   4. sinônimo normalizado.

   A normalização troca por espaço os caracteres proibidos em nome de pasta
   (`/ \ : * ? " < > |`) e o hífen, junta os espaços e passa pra minúsculas. O resto da
   pontuação (`!`, `'`, `,`) continua exigindo igualdade, pra não juntar títulos como `K-ON!` e
   `K-ON!!`. Troca por espaço, não remove, porque o nome da pasta costuma ter espaço onde o
   título tem o caractere proibido (`Re Zero` pra `Re:Zero`); o hífen entra porque é o outro
   substituto usado pra barra (`Fate-stay` pra `Fate/stay`). Título vem antes de sinônimo porque
   um sinônimo genérico (ex.: `Mirai Nikki OVA` como sinônimo de `Mirai Nikki: Redial`) não
   pode ganhar de um título que é exatamente o nome da pasta.
4. Mais de um resultado no mesmo nível: fica o único com número de episódios igual à quantidade
   de arquivos da pasta (`VideoFileService.countByFolder`). Nenhum nível bate, ou o empate
   continua: `MATCH_NOT_FOUND` (com os candidatos, no caso do empate) → `UNRESOLVED`.
5. `MATCH_SELECTED`. Anime escolhido já sincronizado com outra pasta: `ERROR` → `FAILED`.
6. Número de episódios conhecido e diferente da quantidade de arquivos: `WARNING` com os dois
   números, sem bloquear — a divergência costuma ser legítima (episódios curtos compilados num
   arquivo, filme em mais de um arquivo, episódio faltando).
7. `AnimeService.bindAnime` (com retry de rate limit) → `COMPLETED`.

Qualquer exceção (AniList fora, bind recusado) vira `ERROR` com a mensagem → `FAILED`.

**Step `TMDB_BIND`** (`TmdbBindStepService.execute`):

1. `STARTED`. Pasta sem anime vinculado: `ERROR` → `FAILED`. Anime já vinculado ao TMDB:
   `MATCH_SELECTED` + `COMPLETED`, mantém o vínculo.
2. Resolve os candidatos do anibridge (`AnibridgeResolutionService.resolveCandidates`) pelo
   `anilistId` (ou `malId`), com o total de episódios e o formato do anime, todos já salvos —
   sem chamar a AniList. Nenhum candidato → `MATCH_NOT_FOUND` → `UNRESOLVED`.
3. Também termina `UNRESOLVED` com `MATCH_NOT_FOUND`:
   - quando há mais de um candidato e o melhor é suspeito (sobreposição ou cobertura maior que
     o anime). Com um candidato só, a sobreposição não impede o bind: costuma ser o anibridge
     numerando os mesmos episódios de dois jeitos na mesma série, o que só importa pro pairing
     de episódios;
   - quando o melhor empata com outro em todos os critérios (`CandidateResolution.tied`) —
     típico de OVA/especial que existe no TMDB como filme e como especial da série.
4. `MATCH_SELECTED` → confere o título no TMDB (`AnimeTmdbService.fetchDetails`, com retry de
   rate limit): inexistente (dado do anibridge desatualizado) → `MATCH_NOT_FOUND` →
   `UNRESOLVED`. A conferência é separada do bind pra que só o 404 do título vá pra
   curadoria, e deixa a ficha em cache pro bind.
5. `AnimeTmdbService.bindTmdb` (com retry de rate limit) → `COMPLETED`. O bind não preenche os
   overrides de imagem.

Qualquer outra exceção (TMDB fora, bind recusado) vira `ERROR` com a mensagem → `FAILED`.

**Step `TMDB_OVERRIDES`** (`TmdbOverridesStepService.execute`):

1. `STARTED`. Pasta sem anime vinculado, ou anime sem vínculo com o TMDB: `ERROR` → `FAILED`.
2. `REQUEST_SENT` → `AnimeTmdbOverridesFillService.fillOverrides` (com retry de rate limit),
   que escolhe e grava título, sinopse, backdrop e logo pelos idiomas configurados (regras no
   README de `anime`): título, sinopse e backdrop caem no idioma de
   `tmdb.override.fallback-language`; o logo segue só a própria lista de idiomas. O backdrop
   só é preenchido se o anime ainda não tem; os demais campos são sobrescritos. Traduções e
   imagens são buscadas fora de transação, e só a gravação roda numa transação curta.
3. Um `WARNING` por campo sem opção no TMDB nos idiomas configurados (o campo fica `null`), sem
   bloquear → `COMPLETED`, com os overrides gravados nos `details`.

Qualquer exceção (TMDB fora, título inexistente no TMDB, preferência de idioma com valor
inválido) vira `ERROR` com a mensagem → `FAILED`.

**Step `TMDB_FILEPAIR`** (`TmdbFilePairStepService.execute`):

1. `STARTED`. Pasta sem anime vinculado, ou anime sem vínculo com o TMDB: `ERROR` → `FAILED`.
   Vínculo de filme: `SKIPPED` (evento) → `SKIPPED`, já que filme não tem episódios.
2. `REQUEST_SENT` → `AnimeTmdbFilePairingFillService.fillPairings` (com retry de rate limit),
   que resolve cada arquivo pelo anibridge (`EpisodePairingResolutionService`, a mesma
   resolução da proposta de pairing do portal) e grava só o que não precisa de decisão:
   - arquivo que já tem pairing gravado é mantido como está, mesmo que a resolução aponte
     outro episódio — uma nova ingestão não desfaz o pairing curado à mão;
   - episódio resolvido pra mais de um arquivo, ou já gravado em outro arquivo da pasta, não é
     gravado em nenhum dos envolvidos (`EPISODE_CONFLICT`).

   A resolução roda fora de transação; só a gravação roda numa transação curta.
3. `RESULTS_FOUND` com pareados agora, já pareados e sem par → um `WARNING` por arquivo sem
   par, com o motivo (status da resolução ou `EPISODE_CONFLICT`) explicado na mensagem e o
   código nos `details`; em `OUT_OF_AIR_WINDOW`, os `details` trazem também o período do anime
   (`animeStartDate`/`animeEndDate`).
4. Nenhum arquivo pareado, nem por pairing que já existia (inclui `NO_MAPPING` e
   `CONFLICTING_MAPPING`, que valem pra pasta inteira): `MATCH_NOT_FOUND` → `UNRESOLVED`.
   Pareamento parcial: `COMPLETED`.

Qualquer exceção (anibridge fora, TMDB fora) vira `ERROR` com a mensagem → `FAILED`. A busca
de uma temporada que falha no TMDB não derruba o step: a resolução marca os arquivos daquela
temporada como `NOT_FOUND_IN_TMDB`.

## Arquivos críticos

- `service/IngestionService.java` — disparo em duas fases; a ordem e a transação por pasta da
  fase sequencial garantem o `parent` das pastas.
- `service/IngestionTaskService.java` — cadastro dos vídeos e da task; a checagem de arquivo
  existente antes de criar evita o rollback silencioso da task.
- `service/IngestionTaskExecutionService.java` — execução sequencial dos steps, retomada e
  status final da task.
- `service/step/IngestionStepService.java` — contrato dos steps e abertura da execução.
- `service/step/StepRun.java` — gravação dos eventos e fechamento de uma execução de step.
- `service/step/AnilistSyncStepService.java` — escolha do título da AniList e bind; a regra de
  comparação de nomes decide o que é vinculado automaticamente.
- `service/step/TmdbBindStepService.java` — escolha do título do TMDB pelo anibridge e bind; as
  regras de suspeita e empate decidem o que vai pra curadoria.
- `service/step/TmdbOverridesStepService.java` — preenchimento dos overrides do anime a partir
  do TMDB.
- `service/step/TmdbFilePairStepService.java` — pairing dos arquivos da pasta com os episódios
  do TMDB.
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
- A criação das tasks roda sem limite de paralelismo — uma thread virtual por pasta, cada uma
  com sua transação, disputando o pool de conexões do datasource.
- Pastas e arquivos que sumiram do provider não são removidos do catalog.
- Pasta já existente é reaproveitada como está pelo `findOrCreate`: `mediaType` e `parent` não são
  atualizados, então uma pasta gravada sem pai continua sem pai numa nova ingestão.
- Não há como reprocessar uma task `FAILED` ou `WAITING_CURATION`: o scheduler só toma
  `PENDING` e `RUNNING`.
- A busca na AniList envia o nome da pasta como está e olha só os 5 primeiros resultados: nome
  de pasta fora do padrão (sufixos como `(2)`, `x2` no lugar de `×2`) pode não trazer o título
  certo, e a task vai pra curadoria.
- O retry de rate limit (`RetryUtils.withRateLimitRetry`) não tem limite de tentativas: sob rate
  limit prolongado da AniList, a task fica parada e os ciclos seguintes são pulados até ela
  terminar.
- O `EpisodeNumberExtractor` não extrai número de alguns padrões de nome — número entre
  underscores ou após prefixo (`_Sunshine!!_01[`, `_S2_01_v2_`, `_Season2_01_`, `_E02 -`) —, e
  esses arquivos ficam sem pairing no step `TMDB_FILEPAIR`, a não ser que a pasta inteira saia
  sem número e tenha exatamente o total de episódios do anime — aí a numeração da pasta
  (`FolderEpisodeNumbering`, no `anime`) usa a ordem dos nomes. Mexer nos patterns
  exige comparar a extração antes e depois sobre os nomes reais do catálogo, pelo risco de
  regressão descrito em "Arquivos críticos". **Backlog real** (confirmado pelo mantenedor).
- `IngestionResource` não tem `@ApplicationScoped` explícito na classe (diferente de
  `CacheResource`); sem anotação de escopo, o CDI trata a classe como `@Dependent` — o JAX-RS
  resource funciona normalmente, só sem o pooling de `@ApplicationScoped`. **Backlog real**
  (confirmado pelo mantenedor).
