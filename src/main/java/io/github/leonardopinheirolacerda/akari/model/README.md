# model (raiz)

## Objetivo do módulo
Abrigar tipos genéricos cross-domínio que não têm um dono de domínio único — pela convenção do projeto, um tipo só deveria entrar aqui se usado por 2 ou mais domínios.

## Responsabilidade principal
Fornecer formas de dados reutilizáveis entre domínios: um envelope de página genérico (`PageResult`) e um par payload/timestamp para leitura de cache (`CacheEntry`). Nenhum dos dois tipos tem comportamento além de ser um `record` — não há lógica de negócio aqui.

## Funcionalidades existentes
- **`PageResult<T>`**: `record(data, page, size, totalElements, totalPages)` — página genérica de resultado de consulta Panache. Cada domínio converte para o envelope de página do contrato (ex.: `VideoFilePage`) via seu próprio `*Mapper`.
- **`CacheEntry<T>`**: `record(payload, fetchedAt)` — par payload/timestamp lido do cache do chamador de `CacheUtils.resolve`, agnóstico da entidade concreta.

## Dependências internas e externas
- `CacheEntry` depende de `java.time.OffsetDateTime` e é referenciado por `utils/CacheUtils.java` no Javadoc e na assinatura de `resolve(...)`.
- `PageResult` depende só de `java.util.List`.
- Nenhum dos dois importa `exceptions/`, `config/` ou qualquer `domain/`.

## Módulos relacionados
- `PageResult` é usado por 6 domínios distintos: `anime`, `cache`, `mediafolder`, `notification`, `storageprovider` e `videofile` (nos respectivos `model`/`mapper`/`service` de listagem paginada). Isso está de acordo com a convenção de 2+ domínios.
- `CacheEntry` é usado, fora deste pacote e de `utils/CacheUtils`, **apenas** por `domain/cache/model/IntegrationCache.java` (método `readEntry`).

## Pontos de entrada
Não há pontos de entrada no sentido de endpoint/scheduler — são tipos de dados instanciados por quem os usa (`CacheUtils.resolve`, `IntegrationCache.readEntry`, os `*Mapper`/`service` de paginação).

## Fluxos importantes
- **Cache-first**: `CacheUtils.resolve` recebe um `Supplier<Optional<CacheEntry<T>>>` (leitor de cache do chamador), decide se o `CacheEntry` está fresco (`fetchedAt` dentro do TTL) e devolve o `payload` cacheado ou busca um novo valor no upstream.
- **Paginação**: um `service` de domínio monta um `PageResult<T>` a partir de uma consulta Panache paginada; o `*Mapper` do domínio converte esse `PageResult` genérico no DTO de página específico do contrato.

## Arquivos críticos
- `PageResult.java` — tocado por qualquer mudança no formato de paginação cross-domínio.
- `CacheEntry.java` — tocado por qualquer mudança no contrato de `CacheUtils.resolve`.

## Observações técnicas e débitos identificados
- **Violação confirmada da convenção**: `CacheEntry` é usado por um único domínio (`cache`, via `IntegrationCache.readEntry`) — não há um segundo domínio consumindo esse tipo hoje. A convenção do projeto diz que `model/` raiz só deveria receber um tipo novo quando usado por 2+ domínios; `CacheEntry` não atende esse critério. **Backlog real** (confirmado pelo mantenedor, mesmo item já listado em `ARQUITETURA_DO_SISTEMA.md`): candidato a mover para `domain/cache/model/`.
- `PageResult`, em contraste, cumpre a convenção com folga (6 domínios).
