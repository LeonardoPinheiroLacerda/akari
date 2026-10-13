## Objetivo do módulo

Notificações de observabilidade exibidas ao admin — tag `Notification` do contrato: lista
notificações, conta as não-lidas (badge) e marca como lidas (individual/todas). Serve a API
`/v1/notifications`.

## Responsabilidade principal

Armazenar notificações, listar as mais recentes e gerenciar o estado lido/não-lido. O domínio
também expõe o único ponto de criação (`notify()`), usado internamente — não há endpoint de
criação na API.

## Funcionalidades existentes

- `GET /v1/notifications` — lista as 100 notificações mais recentes (lidas ou não), da mais
  nova para a mais antiga.
- `GET /v1/notifications/unread-count` — contagem de não-lidas, para badge.
- `POST /v1/notifications/{notificationId}/read` — marca uma notificação como lida; idempotente
  (id inexistente ou já lida responde `204` sem alterar nada).
- `POST /v1/notifications/read-all` — marca todas as não-lidas como lidas.
- `notify(level, title, message, folderId, coverImageUrl)` — cria uma notificação; método de
  serviço interno, não exposto via API.

## Dependências internas e externas

- Nenhuma dependência de outro domínio do projeto — confirmado por grep (`akari.domain.*`) nos
  4 arquivos do pacote: nenhum resultado fora de `notification`.
- DTOs gerados: `NotificationResponse`, `UnreadCountResponse`, `NotificationLevel`.

## Módulos relacionados

- `ingestion` (`FolderAutoBindService`, `IngestionService`) — único produtor hoje: chama
  `notificationService.notify()` para sinalizar conclusão, falha geral e falhas parciais do
  binding automático de cada pasta (9+ pontos de chamada em `FolderAutoBindService`, 2 em
  `IngestionService`). O domínio `notification` nunca lê nada de `ingestion`; a relação é
  unidirecional — `notification` é escrito por fora e lido só pela própria API.

## Pontos de entrada

- `NotificationResource` (implementa `NotificationApi` gerado) — camada HTTP, com 4 métodos
  (`getUnreadCount`, `listNotifications`, `markAllNotificationsRead`,
  `markNotificationRead`).
- `NotificationService.notify()` — chamado diretamente por `FolderAutoBindService` e
  `IngestionService`, fora da camada HTTP; é o único produtor de `Notification` no código.

## Fluxos importantes

- `notify()`: cria `Notification` com `read = false` e persiste.
- `markNotificationRead`: busca por id; se presente, seta `read = true`. Idempotente e
  silencioso (sem exceção) se o id não existir.
- `markAllNotificationsRead`: `UPDATE` em lote (`set read = true where read = false`).
- `listNotifications`: `findAll` ordenado por `createdAt desc`, truncado a `RECENT_LIMIT = 100`
  na própria query — não há paginação exposta na API.

## Arquivos críticos

- `model/Notification.java` — queries estáticas `listRecent`, `countUnread`, `markAllAsRead`.
- `service/NotificationService.java` — `notify()` é o único ponto de escrita do domínio.

## Observações técnicas e débitos identificados

- A tag `Notification` descreve "o push em tempo real é um stream SSE em
  `/v1/notifications/events` ... servido por um resource code-first", mas não existe nenhum
  resource SSE para esse path no código ainda. **Não é divergência, é feature planejada**
  (confirmado pelo mantenedor) — o único SSE implementado hoje é `domain/cache`
  (`CacheEventBroadcaster`/`CacheEventResource`).
- Sem paginação real na API: a listagem é sempre top-100 fixo (`RECENT_LIMIT`); qualquer
  notificação além das 100 mais recentes fica inacessível via API, mesmo que não lida —
  pode subestimar o conteúdo real atrás do `unread-count` se houver mais de 100 não-lidas
  acumuladas. **Backlog real** (confirmado pelo mantenedor) — paginação de verdade é candidata
  a cobrir futuramente.
- Não há rotina de limpeza/retenção das notificações antigas visível no código — a tabela
  cresce indefinidamente. **Aceito** (confirmado pelo mantenedor): volume de uso
  pessoal/doméstico não justifica rotina de retenção.
