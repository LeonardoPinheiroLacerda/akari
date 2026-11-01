# ingestion

## Objetivo do módulo

Reconciliar o subtree de um storage provider com o catalog: pastas/arquivos já registrados são preservados, novos são criados e os que não existem mais no provider são removidos — e, opcionalmente, vincular automaticamente cada pasta nova a um anime (AniList/TMDB) e parear seus episódios.

## Responsabilidade principal

Orquestrar chamadas a `storageprovider` (listar árvore remota) e a `mediafolder`/`videofile` (sincronizar catalog), sem persistir estado de negócio próprio. O único estado que o módulo mantém é de controle de execução, em memória (`IngestionRunRegistry`), não uma entidade de domínio.

## Funcionalidades existentes

- `PUT`/`POST /v1/ingestion/providers/{providerId}` (operationId `ingestProviderSubtree`) — dispara a reconciliação de um subtree em background e devolve `202 STARTED` de imediato (`IngestionResource.ingestProviderSubtree` → `IngestionService.ingestProviderSubtree`).
- `GET /v1/ingestion/providers/{providerId}/available-folders` — lista a árvore de pastas remotas do provider marcando quais já foram ingeridas (`IngestionResource.listAvailableFolders` → `IngestionService.listAvailableFolders`).
- `DELETE /v1/ingestion/providers/{providerId}/available-folders` — remove do catalog tudo que foi ingerido sob um subtree, idempotente (`IngestionResource.removeIngestedSubtree` → `IngestionService.removeIngestedSubtree`).
- Dedup de execuções concorrentes por `(providerId, rootPath)` (`IngestionRunRegistry`).
- Extração best-effort do número de episódio a partir do nome do arquivo, usada no pairing automático (`EpisodeNumberExtractor`).
- Auto-bind de pasta nova: busca/vincula AniList → resolve/vincula TMDB via anibridge → pareia episódios (`FolderAutoBindService`).
- Remoção de pastas + anime vinculado, reutilizada pelo `DELETE` explícito e pelo Step 2 da reconciliação (`FolderRemovalService`).

## Dependências internas e externas

- `domain/storageprovider` (`StorageProvider`, `StorageProviderService`, `StorageProviderClientFactory`, `StorageProviderClient`, `StorageItem`) — lista a árvore remota e verifica existência de `rootPath`.
- `domain/mediafolder` (`MediaFolder`, `MediaFolderService`) — cria/busca/remove pastas do catalog.
- `domain/videofile` (`VideoFile`, `VideoFileService`) — cria/lista/remove arquivos de vídeo do catalog.
- `domain/anime` (`Anime`, `AnimeService`, `AnimeOverrideService`, `AnimeRelationsService`, `AnimeTmdbService`, `AnimeTmdbFilePairingService`) — binding de anime à pasta, resolução da capa efetiva (override TMDB ou AniList) pras notificações, e atualização de `reviewStatus`.
- `domain/anilist` (`AnilistSearchService`) — busca de candidato na AniList pelo nome da pasta.
- `domain/anibridge` (`AnibridgeResolutionService`, `MappingSegment`, `ResolvedEpisode`, `TmdbMappingCandidate`) — resolve o mapeamento AniList ↔ TMDB e o episódio TMDB a partir do número extraído.
- `domain/tmdb` (`TmdbService`, `TmdbEpisodeResponse`) — bind TMDB e metadata de episódio (thumbnail, nome, overview etc.).
- `domain/notification` (`NotificationService`) — reporta conclusão/erro da reconciliação e falhas pontuais de auto-bind (`INFO`/`WARNING`/`ERROR`).
- `domain/setting` (`SettingService`) — precheck de `tmdb.api-key` configurado.
- `extractor/EpisodeNumberExtractor` — sem subpacote `model`/`mapper` compartilhado com os demais; é o único ponto por onde `videofile` (`VideoFileService.naturalSortKeyOf`) e `anime` (`EpisodePairingResolutionService`) também consomem esse domínio, reaproveitando a mesma extração pra não duplicá-la.
- `exceptions/` (`BusinessRuleException`, `IntegrationException`, `ResourceNotFoundException`).
- `utils/` (`DirectoryUtils`, `FileNameUtils`, `ThreadUtils`, `RetryUtils`).

## Módulos relacionados

`domain/videofile` (`VideoFileService`, pra calcular `naturalSortKey`) e `domain/anime` (`EpisodePairingResolutionService`, na resolução do pairing) importam `extractor/EpisodeNumberExtractor` — as únicas classes fora de `domain/ingestion` que importam algo daqui. Fora essa exceção, o módulo continua um orquestrador de ponta — consome vários domínios, não é consumido por nenhum outro.

## Pontos de entrada

- `resource/IngestionResource` — implementa `IngestionApi` (gerado do contrato), único ponto de entrada HTTP do domínio.
- `service/IngestionService` — ponto de entrada lógico; dispara a thread virtual que roda o restante do fluxo em background.

## Fluxos importantes

**Disparo** (`IngestionService.ingestProviderSubtree`): valida provider existente e TMDB configurado (síncrono, pode devolver `404`/`400`); tenta registrar a execução em `IngestionRunRegistry` — se já há uma rodando para o mesmo `(providerId, rootPath)`, devolve `202 STARTED` sem nova thread; caso contrário, inicia `Thread.ofVirtual()` executando `runReconciliation` e devolve `202 STARTED` imediatamente.

**Reconciliação em background** (`IngestionService.runReconciliation`), 4 etapas:
1. Lista a árvore remota (`StorageProviderClient.listTree`); falha aqui gera notificação `ERROR` e aborta.
2. `ReconciliationService.reconcileRemovals` (Step 2, uma transação) — remove do catalog pastas/arquivos cujo path não existe mais no provider, via `FolderRemovalService`.
3. Para cada diretório remoto, em ordem de profundidade ascendente, `ReconciliationService.reconcileFolder` (Step 3, uma transação por pasta) — cria a pasta se não existir e os `VideoFile`s diretos novos; arquivo direto já catalogado que ainda não tem `naturalSortKey` (ver README de `videofile`) recebe a chave aqui mesmo, via `VideoFileService.backfillNaturalSortKeyIfMissing`.
4. Para cada pasta recém/já existente com vídeos diretos, `FolderAutoBindService.autoBindFolder` (Step 4) — processado uma pasta por vez, com `sleep` de 1500ms entre pastas (throttle proativo para AniList/TMDB).

Ao final (ou em caso de falha irrecuperável no listTree), a chave é liberada em `IngestionRunRegistry.finish` (bloco `finally`), e uma notificação `INFO` resume quantas pastas foram criadas e quantas passaram por auto-bind.

**Auto-bind** (`FolderAutoBindService.autoBindFolder`, nunca lança): bind AniList (busca por nome, pula candidato já vinculado a outra pasta) → bind TMDB via `AnibridgeResolutionService.resolveBestCandidate` → se TV, pareamento episódio a episódio: a resolução fica em `anime` (`EpisodePairingResolutionService`, compartilhada com a proposta de pairing do portal — ver README de `anime`: número do episódio pelo `EpisodeNumberExtractor`, segments do anibridge só da série vinculada, fallbacks de offset absoluto e de pasta de episódio único, confirmação no TMDB), e o `FolderAutoBindService` grava os arquivos `PAIRED` e notifica o resto. Mapeamento conflitante notifica e não pareia nada; sem mapeamento pra série vinculada, não pareia nada e só marca `PARTIAL`. Arquivo cujo episódio não existe no TMDB (temporada inexistente, episódio fora da temporada ou falha na busca) não é pareado, e uma notificação `WARNING` diz quantos arquivos ficaram de fora e em quais temporadas: o anibridge pode estar dessincronizado do TMDB (ex.: apontar pra uma temporada que o TMDB não tem), e gravar o pairing sem metadata faria o arquivo aparecer como pareado sem thumbnail. Atualiza `reviewStatus` do anime para `PENDING_REVIEW` (completo) ou `PARTIAL` (incompleto), nunca sobrescrevendo um `REVIEWED` manual.

As notificações `WARNING` disparadas depois que o anime já está vinculado à AniList (falha no bind TMDB, conflito/numeração absoluta/episódio inexistente no TMDB/pareamento parcial) levam a capa do anime (`FolderAutoBindService.coverImageOf` → `AnimeOverrideService.resolveThumbnailMedium`). As 3 notificações do próprio `bindAnilist` (busca/bind na AniList falhou ou sem resultado) e a do `listTree`/resumo em `IngestionService` não têm capa — ainda não existe (ou nunca existirá, no caso do resumo que cobre várias pastas) um anime único pra ilustrar.

**Remoção** (`FolderRemovalService.removeFolders`): remove o anime vinculado a cada pasta antes da pasta, porque a FK `anime.folder_id` não tem cascade.

## Arquivos críticos

- `service/IngestionService.java` — ponto único de disparo e orquestração do fluxo completo; concentra as validações síncronas e o `runReconciliation`.
- `service/ReconciliationService.java` — Steps 2-3; cada método é sua própria transação (chamado de uma thread virtual sem transação JTA ambiente).
- `service/FolderAutoBindService.java` — orquestra o auto-bind e traduz a resolução do pairing (`anime.EpisodePairingResolutionService`) em gravação, notificações e `reviewStatus`.
- `extractor/EpisodeNumberExtractor.java` — heurística de regex ajustada empiricamente contra ~10 mil arquivos reais; qualquer alteração de pattern tem risco real de regressão silenciosa no pairing.
- `registry/IngestionRunRegistry.java` — único guardião contra reconciliação concorrente duplicada sobre o mesmo subtree.

## Observações técnicas e débitos identificados

- Confirmado lendo o código: `ingestion` não tem nenhuma entidade Panache própria (sem `@Entity` em todo o domínio) — a descrição da tag no `openapi.yaml` ("não persiste estado próprio") é fiel ao código. O único estado mantido é o `Set` em memória do `IngestionRunRegistry`, explicitamente documentado como não precisando sobreviver a restart.
- `model/FolderReconciliationResult` é um record interno de transporte entre `ReconciliationService` e `IngestionService` (carrega `folderName` para evitar reabrir a `MediaFolder` fora de transação na thread virtual) — não é um "model" de persistência, diferente do que o nome do subpacote sugeriria em outros domínios.
- `IngestionResource` não injeta nada além de `IngestionService` e não tem `@ApplicationScoped` explícito na classe (diferente de `CacheResource`); **confirmado:** sem anotação de escopo, o CDI trata a classe como `@Dependent` (pseudo-escopo padrão) — uma instância nova por injeção/requisição, não compartilhada — então o JAX-RS resource continua funcionando normalmente, só sem o pooling de `@ApplicationScoped`. É inconsistência de anotação frente ao padrão "resource burra" dos demais domínios. **Backlog real** (confirmado pelo mantenedor).
- `runReconciliation` roda em thread virtual sem transação ambiente; cada chamada a `ReconciliationService`/`FolderRemovalService` abre sua própria transação — qualquer novo método adicionado a esses services precisa manter esse padrão (um `@Transactional` por chamada), senão quebra a premissa de que o pai sempre existe no catalog antes do filho ser processado.
- O throttle de 1500ms entre pastas no Step 4 é fixo no código (`AUTO_BIND_THROTTLE_MILLIS`), não configurável via `SettingService`. **Backlog real** (confirmado pelo mantenedor): vale externalizar se o rate limit de AniList/TMDB mudar.
