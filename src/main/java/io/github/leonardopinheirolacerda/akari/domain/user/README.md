# Domínio `user`

## Objetivo do módulo

Perfis de usuário, o histórico de visualização de cada um e a lista de títulos. Tag `User` do
contrato, API `/v1/users`.

## Responsabilidade principal

- Cadastrar e listar os perfis. Um perfil tem só nome: não há senha, login nem token, e o
  `userId` vai no path, na query ou no corpo das rotas que precisam dele.
- Guardar, por usuário e arquivo de vídeo, onde o usuário parou e se já assistiu, a partir do
  heartbeat das sessões de playback.
- Guardar os títulos (animes) que o usuário adicionou à lista dele.

## Funcionalidades existentes

- `GET /v1/users` — lista os perfis, por `id`.
- `POST /v1/users` — cadastra um perfil (`name` obrigatório, não vazio).
- `DELETE /v1/users/{userId}` — remove o perfil e tudo o que é dele (histórico e lista de
  títulos, em cascata no banco); `404` se o usuário não existir. Uma sessão de playback em
  andamento não é interrompida: fica sem usuário, para de gravar progresso e expira pela
  varredura normal do `playback`.
- `GET /v1/users/{userId}/watchlist` — lista de títulos paginada, do adicionado mais
  recentemente pro mais antigo, como `AnimeBindingSummaryPage` (mesmo card das smart lists).
- `POST /v1/users/{userId}/watchlist` — adiciona um anime à lista. Idempotente: adicionar um
  título que já está na lista responde `204` sem fazer nada.
- `DELETE /v1/users/{userId}/watchlist/{anilistId}` — remove um anime da lista. Idempotente:
  remover um título que não está na lista (ou um `anilistId` que nem existe) responde `204`;
  `404` só se o usuário não existir.
- Fora da tag `User`, `GET /v1/metadata/animes/{anilistId}/franchise?userId=` traz
  `inWatchlist`: se o anime da rota (não necessariamente a raiz) está na lista do usuário
  (ver `domain/anime/README.md`).
- Fora da tag `User`, o progresso aparece em `GET /v1/metadata/animes/{anilistId}/episodes?userId=`,
  com o `watchProgress` de cada episódio (ver `domain/anime/README.md`), e é gravado pelo
  heartbeat de `POST /v1/playback/sessions/{sessionId}/heartbeat` (ver
  `domain/playback/README.md`).

## Regras do histórico

- Só grava a sessão iniciada com `userId`, e só no heartbeat que traz `positionSeconds`. Sessão
  sem usuário, heartbeat sem corpo ou sem posição só renovam o TTL.
- `positionSeconds` é a posição na mídia (mesma semântica do seek), não o `currentTime` da
  playlist.
- Uma linha por usuário e arquivo (`UNIQUE (user_id, file_id)`): cada heartbeat sobrescreve a
  posição, a duração e o `lastWatchedAt`. Não há soma de tempo assistido.
- Assistido = alguma posição recebida chegou a 90% da duração (`WATCHED_THRESHOLD`), mesmo
  padrão de Plex/Jellyfin/Emby. Uma vez `true`, continua `true`: rever o episódio do começo não
  desmarca.
- A duração vem do probe feito no início da sessão (`PlaybackSession.durationSeconds`). Sem
  duração no probe, o arquivo nunca vira assistido.

## Dependências internas e externas

- `anime` — `AnimeService.findOrThrow` (valida o anime adicionado à lista) e
  `AnimeOverrideService.toBindingSummary` (card dos itens da lista); `WatchlistItem.anime`
  referencia `Anime`.
- `videofile` — `WatchHistory.videoFile` referencia `VideoFile`.
- `exceptions.ResourceNotFoundException` — usuário ou anime inexistente.

## Módulos relacionados

- **`playback`** usa `UserService.findOrThrow` no início da sessão com `userId` e
  `UserService.recordWatchProgress` no heartbeat com posição; `PlaybackSession.user` referencia
  `User`.
- **`anime`** usa `UserService.findWatchProgressByFiles` na listagem de episódios com `userId` e
  `UserService.isInWatchlist` na franquia com `userId`.
  Com a dependência de `user` em `anime` (lista de títulos), forma o ciclo `anime ↔ user`,
  service a service (ver `docs/ARQUITETURA_DO_SISTEMA.md`).

## Pontos de entrada

- `UserResource` (implementa `UserApi` gerado) — só delega pro `UserService`.

## Fluxos importantes

- `recordWatchProgress`: busca a linha por `(user, file)` ou cria uma nova com
  `watched = false` → grava posição, duração e `lastWatchedAt = now` → se a duração é conhecida
  e a posição ≥ 90% dela, `watched = true`.
- `removeUserWatchlistItem`: valida o usuário → busca o item por `(user, anime)` → apaga se
  existir; sem item, não faz nada.
- `isInWatchlist`: valida o usuário → busca o item por `(user, anime)` (uma linha, pela
  `UNIQUE (user_id, anilist_id)`), sem carregar a lista.
- `findWatchProgressByFiles`: valida o usuário → uma consulta (`videoFile.id in ...`) com os ids
  da página de episódios → mapa `fileId → WatchProgress`; arquivo nunca aberto fica fora do mapa.

## Arquivos críticos

- `service/UserService.java` — cadastro, histórico, lista e a regra de assistido.
- `model/WatchHistory.java` — progresso por usuário e arquivo (`user_watch_history`).
- `model/WatchlistItem.java` — título na lista (`user_watchlist_item`).
- `model/User.java` — perfil (`app_user`; `user` é palavra reservada no Postgres).

## Observações técnicas e débitos identificados

- Não há autenticação: qualquer cliente pode ler e gravar o histórico e a lista de qualquer
  `userId`.
- Remover usuário apaga o histórico e a lista dele (`ON DELETE CASCADE`) e desvincula as sessões
  de playback (`ON DELETE SET NULL`). A sessão não é apagada junto porque apagar a linha direto
  no banco deixaria o `ffmpeg` e a pasta de trabalho órfãos — o encerramento do processo só
  acontece no reinício ou na varredura de expiração do `playback`. Remover um arquivo apaga o
  histórico dele; remover um anime tira ele das listas.
