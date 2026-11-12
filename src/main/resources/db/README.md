# db — Migrations Flyway

Migrations do schema PostgreSQL da aplicação, aplicadas pelo Flyway no boot
(`quarkus.flyway.migrate-at-start=true`, `quarkus.flyway.locations=classpath:db`, configurados em
`application.properties`). Em testes, o Flyway é desligado (`%test.quarkus.flyway.
migrate-at-start=false`) e o schema no H2 (modo de compatibilidade PostgreSQL) sai do próprio
Hibernate, a partir do mapeamento das entidades (`%test.quarkus.hibernate-orm.database.
generation=drop-and-create`) — essas migrations **não** rodam em teste, porque usam sintaxe
específica do Postgres (ex.: `TIMESTAMPTZ` no V1) que o H2 não entende.

## Convenção de nome

`V<versão>__<descrição-em-kebab-case>.sql`, sequencial, sem gaps. Versão nova é sempre a próxima
inteira (`V17__...`), nunca reaproveita ou insere entre versões existentes. Migration já aplicada
em qualquer ambiente **nunca é editada** — correção de schema é sempre uma migration nova.

## Tabelas por domínio

Cada tabela pertence à entidade Panache de um único domínio de negócio (`domain/<nome>/model/`);
não há tabela compartilhada entre domínios além das FKs explícitas abaixo.

| Tabela | Migration | Domínio proprietário | Observação |
|---|---|---|---|
| `storage_provider` | V1 | `storageprovider` | `config` é `JSONB` — guarda a config tipada (já com campos sensíveis obscurecidos pelo rclone antes de persistir). |
| `media_folder` | V2 | `mediafolder` | Auto-referência (`parent_id`) para a árvore de pastas; FK em `storage_provider`. |
| `video_file` | V2, V16 | `videofile` | FK em `media_folder`, `ON DELETE CASCADE`. V16 adiciona `natural_sort_key` (nullable, indexada com `folder_id`+`id`) — chave de ordenação da listagem de episódios. |
| `setting` | V3 | `setting` | PK é a própria `key`; seed inicial de todas as preferências conhecidas insere nesta migration (ver abaixo). `secret=TRUE` marca valor que a API nunca ecoa. |
| `notification` | V4 | `notification` | `folder_id` sem FK declarada (referência solta ao id de `media_folder`, não normalizada). |
| `integration_cache` | V5 | `cache` (lado `IntegrationCache`, não `VideoFileCache`) | Cache key/value genérico de resposta de integração (AniList/TMDB), `payload` em `JSONB`. Note que esta tabela convive em `domain/cache/model/` com `video_file_cache` (V12), que é uma entidade funcionalmente independente — ver observação no `README.md` de `domain/cache/`. |
| `anime` | V6 | `anime` | PK é `anilist_id` (não um id interno gerado); `folder_id` é `UNIQUE` — garante 1:1 pasta↔anime no binding; colunas `tmdb_*`/`override_*` são o eixo de enriquecimento TMDB, nulas enquanto não há binding. |
| `anime_relation` | V7 | `anime` (`AnimeRelation`) | Aresta dirigida do grafo de relações (sequel/prequel/etc.), com `UNIQUE (from, to, type)` para não duplicar a mesma relação. |
| `tmdb_file_pairing` | V8, V14 | `anime` (`TmdbFilePairing`) | PK é o próprio `file_id` (1:1 com `video_file`), não um id gerado. V14 adiciona os campos de metadata do episódio TMDB (nome, sinopse, data, runtime, nota, tipo). |
| `anibridge_mapping_release` / `anibridge_mapping_segment` | V9 | `anibridge` | `_release` guarda qual release do dataset do GitHub foi ingerida; `_segment` é o mapeamento episódio↔temporada propriamente dito. **Confirmado** em `MappingRelease.java`: `_release` tem PK `id` sem `SERIAL` porque é uma linha singleton fixa (`SINGLETON_ID = 1`, sempre a mesma linha é atualizada) — o id não vem do upstream, só não precisa de autoincremento por existir uma única linha. |
| `video_file_cache` | V12 | `cache` (`VideoFileCache`) | Fila/status de download remoto→cache local; PK `UUID`, 1:1 com `video_file` (`UNIQUE`). |
| `playback_session` | V13 | `playback` | Sessão de transcode HLS; PK `UUID` (o `sessionId` da API); sem FK de volta — é limpa por exclusão de linha, não por histórico. |
| `ingestion` | V20 | `ingestion` | Uma execução de ingestão de um subtree; `status` como `TEXT`. |
| `ingestion_task` | V20 | `ingestion` (`IngestionTask`) | Uma por pasta da ingestão. FK em `ingestion` e em `media_folder`, ambas `ON DELETE CASCADE` (remover a pasta remove as tasks dela); FK em `anime(anilist_id)` nullable, `ON DELETE SET NULL`. |
| `ingestion_step_execution` | V20 | `ingestion` (`IngestionStepExecution`) | Uma tentativa de uma etapa de uma task; FK em `ingestion_task`, `ON DELETE CASCADE`; `CHECK (attempt > 0)`. |
| `ingestion_step_event` | V20 | `ingestion` (`IngestionStepEvent`) | Evento da linha do tempo de uma tentativa; FK em `ingestion_step_execution`, `ON DELETE CASCADE`; `details` em `JSONB`. |
| `smart_list` | V22 | `smartlist` | `criteria` é `JSONB` — o filtro da lista (`SmartListCriteria`); os itens não são gravados, são recalculados a cada consulta. Sem FK. |

## Migrations de alteração (não criam tabela nova)

- **V10** — insere duas preferências novas em `setting` (`tmdb.image.logo-language`,
  `tmdb.image.backdrop-language`) — mesmo padrão de seed do V3, incremental.
- **V11** — adiciona `review_status` em `anime` (coluna nova, nullable, sem backfill — registros
  existentes ficam `NULL`).
- **V14** — adiciona 7 colunas de metadata de episódio em `tmdb_file_pairing` (todas nullable, sem
  backfill).
- **V16** — adiciona `natural_sort_key` em `video_file` (nullable) + índice, sem backfill na
  migration. Arquivos criados depois recebem a chave na criação; linhas anteriores ficam `NULL`
  — nenhum fluxo preenche a chave delas.
- **V17** — insere três preferências novas em `setting` (`playback.default.audio-language`,
  `playback.default.subtitle-language`, `playback.default.subtitle-forced`) — mesmo padrão de
  seed do V3, incremental.
- **V18** — organização em franquias: adiciona em `anime` `franchise_assignment` (`AUTO`/
  `MANUAL_MEMBER`/`MANUAL_ROOT`/`HIDDEN`, default `AUTO`) e `franchise_root_id` (auto-referência,
  `ON DELETE SET NULL`, com índice parcial nas linhas não nulas — os membros `MANUAL_MEMBER`),
  e remove `franchise_root_curation`. A franquia de cada anime não é materializada: é resolvida
  em memória a cada leitura (`FranchiseResolver`).
- **V21** — insere `tmdb.override.title-language`, `tmdb.override.synopsis-language` (ambas
  `NULL`) e `tmdb.override.fallback-language` (`en`) em `setting` e apaga
  `tmdb.image.backdrop-language`.

## Seed de `setting` (V3 + V10 + V17 + V21)

A tabela `setting` nasce com um conjunto fixo de chaves já inseridas (não é só DDL vazio):
`cache.max-size-bytes`, `anilist.cache.ttl`, `tmdb.cache.ttl`, `tmdb.api-key` (secret, vazio),
`tmdb.default-language`, `tmdb.include-adult`, `playback.transcode.hardware-acceleration`,
`playback.session.heartbeat-ttl`, `cache.dir`, `playback.work.base-dir`, depois (V10)
`tmdb.image.logo-language`, (V17)
`playback.default.audio-language`, `playback.default.subtitle-language`,
`playback.default.subtitle-forced` e (V21) `tmdb.override.title-language`,
`tmdb.override.synopsis-language`, `tmdb.override.fallback-language`. **Nova preferência que o código passa a
ler via um getter tipado de `SettingService` precisa de uma migration de seed correspondente** —
sem isso, o valor não existe na tabela e o getter falha em runtime no primeiro uso (não no boot;
ver observação equivalente no `README.md` de `domain/setting/`).

## Observações técnicas e débitos identificados

- **Nenhuma migration de `DOWN`/rollback** — Flyway community (usado aqui) não suporta `undo`
  nativamente; revertir um schema aplicado exige uma migration nova que desfaça manualmente.
- **FK ausente em `notification.folder_id`** — é um id solto, não uma referência de banco
  declarada; integridade depende só do código. **Lacuna a cobrir** (confirmado pelo mantenedor):
  deveria ter FK declarada (ex.: `ON DELETE SET NULL`), mesmo o risco prático de órfão sendo baixo
  hoje (nenhum domínio lê `notification` de volta).
- **Mistura de responsabilidades em `integration_cache` vs `video_file_cache`**: ambas vivem sob o
  mesmo domínio Panache (`domain/cache/model/`) mas não têm nenhuma relação de schema entre si.
  **Backlog real** (confirmado pelo mantenedor, mesmo achado já registrado no `README.md` de
  `domain/cache/`).
- **Alterações sem backfill (V11, V14, V16)** são aditivas e nullable — seguras para dados
  existentes, mas significam que linhas antigas ficam com a coluna nova vazia até o próximo
  sync/curadoria, não há migração de dados retroativa. No caso do V16, nenhum fluxo preenche a
  chave das linhas antigas (ver acima).
