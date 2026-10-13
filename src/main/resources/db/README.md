# db — Migrations Flyway

Migrations do schema PostgreSQL da aplicação, aplicadas pelo Flyway no boot
(`quarkus.flyway.migrate-at-start=true`, `quarkus.flyway.locations=classpath:db`, configurados em
`application.properties`). Em testes, o schema sobe no H2 em modo de compatibilidade PostgreSQL —
as mesmas migrations rodam lá também; evite SQL exclusivo de dialeto Postgres que o H2 não suporte
sem necessidade real.

## Convenção de nome

`V<versão>__<descrição-em-kebab-case>.sql`, sequencial, sem gaps. Versão nova é sempre a próxima
inteira (`V15__...`), nunca reaproveita ou insere entre versões existentes. Migration já aplicada
em qualquer ambiente **nunca é editada** — correção de schema é sempre uma migration nova.

## Tabelas por domínio

Cada tabela pertence à entidade Panache de um único domínio de negócio (`domain/<nome>/model/`);
não há tabela compartilhada entre domínios além das FKs explícitas abaixo.

| Tabela | Migration | Domínio proprietário | Observação |
|---|---|---|---|
| `storage_provider` | V1 | `storageprovider` | `config` é `JSONB` — guarda a config tipada (já com campos sensíveis obscurecidos pelo rclone antes de persistir). |
| `media_folder` | V2 | `mediafolder` | Auto-referência (`parent_id`) para a árvore de pastas; FK em `storage_provider`. |
| `video_file` | V2 | `videofile` | FK em `media_folder`, `ON DELETE CASCADE`. |
| `setting` | V3 | `setting` | PK é a própria `key`; seed inicial de todas as preferências conhecidas insere nesta migration (ver abaixo). `secret=TRUE` marca valor que a API nunca ecoa. |
| `notification` | V4 | `notification` | `folder_id` sem FK declarada (referência solta ao id de `media_folder`, não normalizada). |
| `integration_cache` | V5 | `cache` (lado `IntegrationCache`, não `VideoFileCache`) | Cache key/value genérico de resposta de integração (AniList/TMDB), `payload` em `JSONB`. Note que esta tabela convive em `domain/cache/model/` com `video_file_cache` (V12), que é uma entidade funcionalmente independente — ver observação no `README.md` de `domain/cache/`. |
| `anime` | V6 | `anime` | PK é `anilist_id` (não um id interno gerado); `folder_id` é `UNIQUE` — garante 1:1 pasta↔anime no binding; colunas `tmdb_*`/`override_*` são o eixo de enriquecimento TMDB, nulas enquanto não há binding. |
| `anime_relation` | V7 | `anime` (`AnimeRelation`) | Aresta dirigida do grafo de relações (sequel/prequel/etc.), com `UNIQUE (from, to, type)` para não duplicar a mesma relação. |
| `tmdb_file_pairing` | V8, V14 | `anime` (`TmdbFilePairing`) | PK é o próprio `file_id` (1:1 com `video_file`), não um id gerado. V14 adiciona os campos de metadata do episódio TMDB (nome, sinopse, data, runtime, nota, tipo). |
| `anibridge_mapping_release` / `anibridge_mapping_segment` | V9 | `anibridge` | `_release` guarda qual release do dataset do GitHub foi ingerida; `_segment` é o mapeamento episódio↔temporada propriamente dito. **Confirmado** em `MappingRelease.java`: `_release` tem PK `id` sem `SERIAL` porque é uma linha singleton fixa (`SINGLETON_ID = 1`, sempre a mesma linha é atualizada) — o id não vem do upstream, só não precisa de autoincremento por existir uma única linha. |
| `video_file_cache` | V12 | `cache` (`VideoFileCache`) | Fila/status de download remoto→cache local; PK `UUID`, 1:1 com `video_file` (`UNIQUE`). |
| `playback_session` | V13 | `playback` | Sessão de transcode HLS; PK `UUID` (o `sessionId` da API); sem FK de volta — é limpa por exclusão de linha, não por histórico. |

## Migrations de alteração (não criam tabela nova)

- **V10** — insere duas preferências novas em `setting` (`tmdb.image.logo-language`,
  `tmdb.image.backdrop-language`) — mesmo padrão de seed do V3, incremental.
- **V11** — adiciona `review_status` em `anime` (coluna nova, nullable, sem backfill — registros
  existentes ficam `NULL`).
- **V14** — adiciona 7 colunas de metadata de episódio em `tmdb_file_pairing` (todas nullable, sem
  backfill).

## Seed de `setting` (V3 + V10)

A tabela `setting` nasce com um conjunto fixo de chaves já inseridas (não é só DDL vazio):
`cache.max-size-bytes`, `anilist.cache.ttl`, `tmdb.cache.ttl`, `tmdb.api-key` (secret, vazio),
`tmdb.default-language`, `tmdb.include-adult`, `playback.transcode.hardware-acceleration`,
`playback.session.heartbeat-ttl`, `cache.dir`, `playback.work.base-dir`, e depois (V10)
`tmdb.image.logo-language`, `tmdb.image.backdrop-language`. **Nova preferência que o código passa a
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
- **Alterações sem backfill (V11, V14)** são aditivas e nullable — seguras para dados existentes,
  mas significam que linhas antigas ficam com a coluna nova vazia até o próximo sync/curadoria, não
  há migração de dados retroativa.
