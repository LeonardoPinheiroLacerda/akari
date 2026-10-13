## Objetivo do módulo

Notificações de observabilidade exibidas ao admin — tag `Notification` do contrato: lista
paginada, conta as não-lidas (badge), marca como lidas (individual/todas) e empurra notificações
novas em tempo real via SSE. Serve a API `/v1/notifications`.

## Responsabilidade principal

Armazenar notificações, paginá-las, gerenciar o estado lido/não-lido e distribuir cada
notificação nova pros clientes inscritos no stream SSE. O domínio também expõe o único ponto de
criação (`notify()`), usado internamente — não há endpoint de criação na API.

## Funcionalidades existentes

- `GET /v1/notifications` — página de notificações (lidas ou não), da mais nova para a mais
  antiga, 0-based (`page`/`size`), pensada para um botão "ver mais" no cliente.
- `GET /v1/notifications/unread-count` — contagem de não-lidas, para badge.
- `POST /v1/notifications/{notificationId}/read` — marca uma notificação como lida; idempotente
  (id inexistente ou já lida responde `204` sem alterar nada).
- `POST /v1/notifications/read-all` — marca todas as não-lidas como lidas.
- `GET /v1/notifications/events` — stream SSE, code-first (fora do contrato, mesmo molde de
  `/v1/cache/events`): emite um evento `NEW` com a notificação recém-criada a cada chamada de
  `notify()`.
- Limpeza periódica de sinks SSE fechados (`NotificationScheduler`, a cada 60s) — ver
  "Observações técnicas".
- `notify(level, title, message, folderId, coverImageUrl)` — cria uma notificação e publica o
  evento SSE; método de serviço interno, não exposto via API.

## Dependências internas e externas

- Nenhuma dependência de outro domínio do projeto — confirmado por grep (`akari.domain.*`) nos
  arquivos do pacote: nenhum resultado fora de `notification`.
- DTOs gerados: `NotificationResponse`, `NotificationPage`, `UnreadCountResponse`,
  `NotificationLevel`.
- `model.PageResult` (fora de `domain/`) — usado por `Notification.findPage`/
  `NotificationMapper.toPage`.

## Módulos relacionados

- `ingestion` (`FolderAutoBindService`, `IngestionService`) — único produtor hoje: chama
  `notificationService.notify()` para sinalizar conclusão, falha geral e falhas parciais do
  binding automático de cada pasta (9+ pontos de chamada em `FolderAutoBindService`, 2 em
  `IngestionService`). O domínio `notification` nunca lê nada de `ingestion`; a relação é
  unidirecional — `notification` é escrito por fora e lido só pela própria API (e pelo stream
  SSE, que todo cliente inscrito recebe).

## Pontos de entrada

- `resource/NotificationResource` (implementa `NotificationApi` gerado) — camada HTTP, com 4
  métodos (`getUnreadCount`, `listNotifications`, `markAllNotificationsRead`,
  `markNotificationRead`).
- `resource/NotificationEventResource` — code-first, expõe `/v1/notifications/events` (SSE), sem
  interface gerada, no mesmo molde de `CacheEventResource`.
- `service/NotificationService.notify()` — chamado diretamente por `FolderAutoBindService` e
  `IngestionService`, fora da camada HTTP; é o único produtor de `Notification` no código.
- `scheduler/NotificationScheduler` — dispara `NotificationService.pruneClosedSseConnections()` a
  cada 60s; nada externo chama esse método diretamente.

## Fluxos importantes

- `notify()`: cria `Notification` com `read = false`, persiste, converte pro DTO de resposta via
  `NotificationMapper` e publica `NotificationEvent.created(...)` no
  `NotificationEventBroadcaster` — todo sink SSE aberto recebe o evento `NEW` na hora.
- `markNotificationRead`: busca por id; se presente, seta `read = true`. Idempotente e
  silencioso (sem exceção) se o id não existir.
- `markAllNotificationsRead`: `UPDATE` em lote (`set read = true where read = false`).
- `listNotifications`: `Notification.findPage(page, size)` (Panache, `order by createdAt desc`)
  → `NotificationMapper.toPage` monta o envelope paginado do contrato.
- `subscribe` (SSE): `NotificationEventResource` delega pro `NotificationEventBroadcaster`, que só
  registra o sink — diferente do `cache`, não há snapshot/estado ativo a enviar na inscrição, a
  conexão fica esperando o próximo `notify()`.
- **Limpeza de sinks mortos** (`NotificationScheduler`, a cada 60s): chama
  `NotificationService.pruneClosedSseConnections()` → `NotificationEventBroadcaster.pruneClosedSinks()`
  remove da lista qualquer sink que o cliente já fechou, sem depender de uma notificação nova
  acontecer — ver "Observações técnicas" sobre por que isso é necessário aqui e não no `cache`.

## Arquivos críticos

- `model/Notification.java` — queries estáticas `find`, `findPage`, `countUnread`,
  `markAllAsRead`.
- `service/NotificationService.java` — `notify()` é o único ponto de escrita do domínio e o único
  que publica no stream SSE.
- `sse/NotificationEventBroadcaster.java` — ciclo de vida dos sinks SSE; bugs aqui vazam conexões
  ou perdem eventos.

## Observações técnicas e débitos identificados

- Não há rotina de limpeza/retenção das notificações antigas visível no código — a tabela
  cresce indefinidamente. **Aceito** (confirmado pelo mantenedor): volume de uso
  pessoal/doméstico não justifica rotina de retenção.
- O stream SSE só emite `NEW` (notificação criada) — marcar como lida (individual ou em lote) não
  gera evento. Badge de não-lidas em múltiplas abas sincroniza só via `GET /unread-count`, não via
  SSE. Não era parte do pedido original (só "notificação nova em tempo real"); considerar se vale
  a pena no futuro, caso o multi-aba/multi-cliente vire requisito real.
- **Risco de sink SSE morto acumulando, resolvido:** diferente do `cache`, este domínio não tem
  fila/scheduler própria que force a limpeza de sinks fechados como efeito colateral — sem
  atividade, um sink morto ficaria preso na lista até a próxima notificação (que pode ser rara).
  Resolvido com `NotificationScheduler` (a cada 60s, `ConcurrentExecution.SKIP`, try/catch — mesmo
  padrão do `CacheScheduler`), chamando `NotificationEventBroadcaster.pruneClosedSinks()`
  independente de qualquer `notify()` acontecer.
- O scheduler só confia em `SseEventSink.isClosed()` — não há heartbeat/ping enviado ao cliente,
  então uma conexão "meio-aberta" (cliente caiu sem fechar limpo, firewall engoliu o FIN) pode não
  ser detectada como fechada até a camada de transporte (Vert.x/Netty) perceber a queda. Não há
  `quarkus.http.idle-timeout` configurado no projeto para dar um teto a isso. Não é um problema
  novo deste domínio — o `cache` tem a mesma limitação — e não foi pedido corrigir agora.
