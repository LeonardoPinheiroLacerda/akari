# Arquitetura do Sistema — akari

> Documento gerado a partir da análise do código-fonte real (todos os pacotes de
> `src/main/java/.../akari`), do contrato `src/main/resources/META-INF/openapi.yaml`, do `pom.xml`,
> `application.properties`, `docker-compose.yml` e do skill `code-style` já mantido pelo projeto.
> Complementa os `README.md` locais de cada pacote (listados em "Dependências críticas" e nas
> seções de cada módulo). Inferências não confirmadas diretamente pelo código estão marcadas como
> **Hipótese**. Base oficial para Spec-Driven Development — ao mudar a arquitetura, atualize este
> arquivo, o `openapi.yaml` e os `README.md` locais afetados juntos.

## Visão arquitetural do projeto

akari é um **módulo único Quarkus** (sem multi-módulo, sem arquitetura hexagonal, sem camada de
domínio paralela à entidade), **contract-first**: o contrato `openapi.yaml` gera as interfaces
JAX-RS (`*Api`) e os DTOs (`target/generated-sources/openapi/.../akari/api` e `.../api/dto`), e o
código-fonte implementa essas interfaces. A arquitetura é deliberadamente simples e mais acoplada
do que uma arquitetura em camadas tradicional — o objetivo explícito (documentado no skill interno
`code-style`) é que uma funcionalidade nova mexa em poucos arquivos.

Todo domínio de negócio mora em `domain/<nome>`, isolado por pacote, com subpacote por tipo de
componente dentro dele (`model/`, `resource/`, `service/`, `mapper/`, `client/`, `scheduler/`, e
subpacotes nomeados por responsabilidade quando necessário: `parser/`, `extractor/`, `registry/`,
`provider/`, `transcode/`, `sse/`). Fora de `domain/`, só existe o que é genuinamente transversal:
`exceptions/`, `config/`, `utils/`, `model/` (raiz).

Fluxo padrão de uma requisição:

```
*Resource (implementa *Api gerado, camada burra)
    → *Service (@ApplicationScoped, regra de negócio, injeta *Mapper)
        → model (Panache active record) e/ou client (REST externo) e/ou service de outro domínio
```

O sistema tem 13 domínios de negócio hoje: `storageprovider`, `ingestion`, `mediafolder`,
`videofile`, `cache`, `mediaprobe`, `playback`, `anilist`, `tmdb`, `anime`, `anibridge`,
`setting`, `notification`.

## Padrões utilizados

- **Contract-first REST**: contrato único em `openapi.yaml`, gerado via
  `openapi-generator-maven-plugin` (`generate-sources`), consumido como interface pelo `*Resource`.
  DTOs de API nunca são escritos à mão.
- **Active Record (Hibernate ORM with Panache)**: entidades estendem `PanacheEntityBase`, consultas
  de negócio vivem como métodos estáticos na própria entidade — não existe camada de repository.
- **Mapper dedicado (MapStruct)**: um `*Mapper` por domínio, `@Mapper(componentModel = "cdi")`,
  injetado só no `*Service`. Converte model ↔ DTO gerado e resposta de client → model.
- **Cliente REST declarativo (MicroProfile REST Client)**: um `*Client` por integração externa
  (`@RegisterRestClient(configKey = "<sistema>")`), com DTOs do upstream isolados em
  `client/dtos/`.
- **Cache-first para integrações externas**: `anilist` e `tmdb` consultam `IntegrationCache`
  (entidade Panache persistida em **PostgreSQL**, coluna JSON) antes de chamar o upstream;
  `forceRefresh=true` força a busca.
- **Scheduler fino**: tarefas `@Scheduled` isoladas em `*Scheduler`, que só delegam para o
  `*Service` correspondente — nunca lógica de negócio no scheduler.
- **Exceção tipada → resposta padronizada**: toda exceção de negócio estende `AppException`
  (status HTTP + `ApiErrorCode` fixos); `@ServerExceptionMapper`s em `exceptions/mapper/`
  convertem para o shape `ApiError` do contrato.
- **SSE code-first fora do contrato**: streams de eventos em tempo real (ex.: progresso de
  cache) não são modelados no OpenAPI (o gerador `jaxrs-spec` não descreve SSE) — são resources
  JAX-RS escritos à mão, fora do fluxo `*Api` gerado.

## Regras arquiteturais

- Nunca reintroduzir `*UseCase`/`*Port`/`*Adapter`/`*Repository` por cima do Panache — a camada de
  persistência é direta via active record.
- `domain/` nunca compete com `exceptions/`/`config/`/`utils/`/`model/` por ser "mais um pacote de
  tipo" — esse espaço é reservado a domínios de negócio.
- Resource é camada burra: implementa a `*Api` gerada, só delega ao `*Service`, nunca vê `model`,
  nunca chama `*Mapper`, nunca monta `Response` de erro.
- Um service pode injetar o service de **outro** domínio quando a regra atravessa domínios; nunca
  acessa o `model` de outro domínio diretamente.
- `model/` (raiz, fora de `domain/`) é reservado a tipos **usados por 2+ domínios** — novo tipo
  aqui exige esse critério (ver violação real na seção de riscos).
- `exceptions/` nunca importa `exceptions/mapper/` — a dependência é unidirecional
  (`mapper` → `exceptions`), e o Javadoc das exceções cita os mappers com `{@code}`, não `{@link}`,
  justamente para não forçar esse import. Essa regra foi verificada íntegra em todo o pacote.
- Domínio novo é só um pacote novo em `domain/`, sem necessidade de aviso prévio. Pacote
  transversal novo (irmão de `domain`, `exceptions`, `config`, `utils`, `model`) exige aviso.

## Convenções técnicas

Resumo das convenções já documentadas em detalhe no skill `code-style` (`.claude/skills/code-style/
SKILL.md`), que permanece a fonte normativa para estilo de código Java do projeto:

- Injeção sempre por campo (`@Inject`, nunca por construtor), em duas linhas, nome do campo
  preserva o sufixo da classe.
- `final` em toda variável local; sem `var`; sem `import static`; sem wildcard import.
- Numéricos em assinaturas são wrappers (`Integer`/`Long`); `int` primitivo só em loop/constante
  privada.
- Method chain (stream, builder, `Optional`) sempre um operador por linha, mesmo cadeia curta.
- Javadoc em português, primeira sentença resumo, `@link` sem FQN.
- Toda exceção de REST client é embrulhada em `IntegrationException` pelo service que a chama —
  nunca escapa crua.
- Paginação da API é 0-based em toda a aplicação (`size` padrão 12, envelope
  `data`/`page`/`size`/`totalElements`/`totalPages`), mesmo quando o upstream integrado (TMDB:
  1-based, blocos de 20; AniList: 1-based) usa convenção diferente — a conversão é responsabilidade
  do módulo de integração, nunca do cliente da API akari.

## Separação de responsabilidades

| Camada | Pacote | Responsabilidade | Nunca faz |
|---|---|---|---|
| Resource | `domain/<x>/resource/` | Implementa `*Api` gerado, delega ao service | Mapper, regra de negócio, `Response` de erro |
| Service | `domain/<x>/service/` | Regra de negócio, injeta mapper/clients/services de outros domínios | `@Transactional` fora de método de escrita |
| Model | `domain/<x>/model/` | Entidade Panache ou record interno sem persistência | Lógica de orquestração entre domínios |
| Mapper | `domain/<x>/mapper/` | Conversão model↔DTO, sem lógica de negócio | Chamada de rede, persistência |
| Client | `domain/<x>/client/` | Transporte cru para sistema externo | Tratamento/retry de erro (é do service) |
| Scheduler | `domain/<x>/scheduler/` | Dispara `@Scheduled`, delega ao service | Regra de negócio |
| `exceptions/` | raiz | Tipos de erro de negócio (`AppException`) | Montagem de resposta HTTP |
| `exceptions/mapper/` | subpacote | `ApiError` a partir de cada exceção | Lançar exceção de negócio |
| `config/` | raiz | Health checks, configuração transversal | Regra de negócio |
| `utils/` | raiz | Helpers puros sem estado | Conhecer múltiplos domínios (ver exceção documentada) |
| `model/` (raiz) | raiz | Tipo genérico usado por 2+ domínios | Tipo usado por um único domínio |

## Fluxo de comunicação entre módulos

Comunicação é sempre **service → service** (injeção direta, chamada síncrona em processo — não há
mensageria/fila entre domínios) ou **service → client** (REST/processo externo). Não existe
barramento de eventos interno; o único mecanismo "assíncrono" real é a fila serial do `cache`
(processada por um scheduler) e os processos `ffmpeg`/`ffprobe` executados como subprocessos do
JVM.

Grafo de dependências entre domínios, confirmado por grep bidirecional (`import
akari.domain.<x>`) feito por domínio:

```
storageprovider   (folha — não depende de nenhum outro domínio)
setting           (folha — não depende de nenhum outro domínio)
notification      (folha — não depende de nenhum outro domínio; só recebe escrita)

mediafolder     → storageprovider, anime
videofile       → mediafolder, anime
cache           → storageprovider, videofile (via utils, não import direto de domain)
mediaprobe      → storageprovider, setting, videofile
anilist         → cache (IntegrationCache)
tmdb            → cache (IntegrationCache), setting
anibridge       → tmdb
anime           → anilist, tmdb, mediafolder, videofile, setting
playback        → videofile, mediaprobe, storageprovider, setting (+ cache via utils)
ingestion       → storageprovider, mediafolder, videofile, anime, anibridge, tmdb, notification
```

Nenhum domínio depende de `playback`, `mediaprobe`, `cache`, `notification` ou `ingestion` — são
"topo" do grafo, consumidos só pela camada HTTP (ou, no caso de `notification`, só escritos por
`ingestion` e nunca lidos de volta por outro domínio).

**Acoplamento atípico identificado:** o service de busca do TMDB (`tmdb.service.
TmdbService`) expõe só 2 operações via `resource/` (`/v1/tmdb/search/movies` e
`/tv-shows`), mas implementa internamente toda a "ficha completa" (detalhes de filme/série,
imagens, traduções, temporada) — e essas operações internas são chamadas diretamente, **service a
service**, por `anime`, `anibridge` e `ingestion`. Ou seja, o domínio `tmdb` funciona como o
client TMDB de fato para todo o sistema, não só para a busca — qualquer mudança de assinatura
nesse service tem raio de impacto em 3 domínios além do seu próprio resource.

## Dependências críticas

- **PostgreSQL** — único banco, schema único, FKs reais entre domínios (ex.: `videofile` →
  `mediafolder`). Migrações via Flyway (`classpath:db`). Em testes, H2 em modo de compatibilidade
  PostgreSQL substitui o Postgres real.
- **rclone (`rcd` local, não containerizado)** — obrigatório para qualquer storage provider `MEGA`:
  resolve listagem/download remoto (`RcloneClient`) e obscurece credenciais sensíveis
  (`POST /core/obscure`) antes de persistir. Roda fora de container de propósito, para compartilhar
  o filesystem do host com a aplicação (relevante para `cache.dir`). Confirmado em código: sobe
  sem autenticação (`--rc-no-auth`) em `127.0.0.1:5572` (`rclone-rcd.sh:19-23`), e a aplicação
  aponta para essa instância via `quarkus.rest-client.rclone.url` (`application.properties:21-23`);
  não há usuário/senha de rc configurado em nenhum lugar.
- **ffprobe / ffmpeg** — binários externos resolvidos via `PATH` por padrão (`akari.mediaprobe.
  ffprobe.binary`, `akari.playback.ffmpeg.binary`). `mediaprobe` e `playback` dependem deles
  diretamente via `ProcessBuilder`/client dedicado; falha desses binários é sempre embrulhada em
  `IntegrationException` (502).
- **AniList (GraphQL)** e **TMDB (REST)** — fontes de metadata de anime; TMDB exige token vindo da
  preferência administrativa `tmdb.api-key` (domínio `setting`), montado por chamada (não há config
  estática de credencial).
- **GitHub (REST, só para `anibridge`)** — descobre a release mais recente do dataset de mapeamento
  de episódios; o download do asset em si usa `HttpClient` puro (não o REST client padrão do
  projeto), por precisar seguir redirecionamento para CDN.
- **`setting` é o domínio mais amplamente consumido do sistema**: zero dependências de saída, mas
  consumido por `tmdb`, `anilist` (confirmado: `AnilistSearchService` injeta `SettingService` e usa
  `getAnilistCacheTtl()` para o TTL de cache), `cache`, `playback`, `mediaprobe`, `ingestion` e
  `anime` via ~13 getters tipados — qualquer regressão nesse service afeta a maior parte da
  aplicação.
- **`storageprovider` é o segundo domínio mais consumido**: folha no grafo (não depende de nada),
  mas é pré-requisito funcional de `ingestion`, `cache`, `playback`, `mediafolder` e `mediaprobe`.

## Riscos técnicos e acoplamentos importantes

Consolidado dos achados por domínio (ver `README.md` de cada pasta para o detalhe completo). Os
itens abaixo são backlog real (a corrigir aos poucos), exceto onde indicado "Aceito".

1. **`domain/cache` mistura dois domínios funcionalmente independentes no mesmo pacote**:
   `VideoFileCache` (fila de download remoto→local com eviction LRU) e `IntegrationCache` (cache
   key/value genérico de respostas AniList/TMDB). Não há relação funcional entre os dois além de
   compartilharem o nome do pacote.
2. **`playback` tem débitos de ciclo de vida de processo**: processos `ffmpeg` e diretórios de
   workspace HLS órfãos em caso de crash da aplicação (sem reconciliação na inicialização); sem
   lock explícito por sessão (corrida possível em seek/troca de faixa concorrente); sem limite de
   sessões concorrentes para o mesmo arquivo. Mitigação real existente: `ProcessTerminationService`
   confirma posse do pid por timestamp antes de matar, com SIGTERM→grace period→SIGKILL.
3. **Aceito**: `RemoteStorageProviderClient.checkConnection()` pode reportar `healthy=true` com
   sessão MEGA cacheada stale no rclone; só reiniciar o `rcd` resolve (`fscache/clear` não é
   suficiente). Afeta diretamente `GET /v1/storage/providers/{id}/health` para providers MEGA.
4. **Retry de rate-limit ad hoc, não centralizado**: `RetryUtils.withRateLimitRetry` é aplicado em
   alguns call sites específicos (`anime.prefillImageOverrides`, `ingestion.FolderAutoBindService`),
   não dentro dos services `anilist`/`tmdb` — quem chama e esquece de envolver a chamada corre risco
   de falha dura sob rate limit do upstream.
5. **`anime`**: recursão não limitada em `syncRelations` dentro de uma única `@Transactional`, com
   uma chamada de rede por vizinho novo — risco de transação longa em franquias grandes;
   `getTmdbFilePairingSchema` faz N chamadas sequenciais ao TMDB (uma por temporada); ausência de
   paginação em lookups em lote (`getAnimeBindings`/`getTmdbFilePairings`); possível corrida no
   bind (check-then-persist sem lock explícito, mitigada só pela PK do banco).
6. **`ingestion`**: `EpisodeNumberExtractor` usa regex calibrada empiricamente — risco de regressão
   silenciosa se alterada sem reteste; throttle de 1500ms entre pastas no auto-bind é hardcoded, não
   configurável via `setting`; `IngestionResource` não tem `@ApplicationScoped` (inconsistência
   pontual com o padrão dos demais resources).
7. **`mediafolder`/`videofile`**: `MediaFolderService.create()` e `VideoFileService` não validam
   duplicidade de path/nome na escrita (assumem que o chamador — tipicamente `ingestion` — já
   garantiu); `deleteRoots()` de `mediafolder` é documentado no próprio código como workaround para
   um falso positivo do Hibernate (`TransientPropertyValueException`) e só funciona corretamente se
   todas as pastas do subtree vierem da mesma consulta `findSubtree`; `listVideoFiles` devolve
   página vazia (não 404) para `folderId` inexistente, podendo mascarar um id inválido do chamador.
8. **`setting`**: escrita (`PUT /v1/settings/{key}`) não valida formato/tipo — falha só no próximo
   uso do getter tipado, em runtime; preferência nova sem seed/migração só quebra no primeiro uso,
   não no boot. A flag `secret` não ser gerenciável via API é **intencional** (confirmado pelo
   mantenedor) — decisão de segurança, não lacuna.
9. **`anibridge`**: lógica densa de resolução de candidato TMDB (critério de "suspeito", merge de
   ranges sobrepostos) em `AnibridgeResolutionService`, sem evidência de testes de unidade nos
   arquivos analisados — candidato a dívida de cobertura; substituição total da tabela
   `MappingSegment` a cada refresh (`deleteAll` + `persist`) é correta mas sensível a crescimento
   de volume.
10. **Aceito** (confirmado pelo mantenedor, ver `domain/anilist/README.md`): `anime` consome DTOs
    crus do client GraphQL da AniList (`AnilistMediaResponse`, `AnilistRelationEdgeResponse`)
    diretamente, em vez de um DTO de domínio próprio — qualquer mudança no schema da AniList
    propaga direto para dentro de `anime`. O shape da AniList é rico; um DTO intermediário
    duplicaria praticamente o mesmo shape sem ganho real.
11. **`utils/VideoFileLocationUtils` é o único utilitário "impuro"** do pacote `utils/`: conhece
    3 domínios diretamente (`cache`, `storageprovider`, `videofile`), diferente dos demais arquivos
    do pacote, que são puros. Documentado no próprio Javadoc como lógica compartilhada entre
    `mediaprobe` e `playback` — intencional, não acidental.

## Diretrizes para futuras implementações

- Ao criar um domínio novo, seguir a estrutura `domain/<nome>/{model,resource,service,mapper,
  client,scheduler}` do skill `code-style` sem introduzir camadas paralelas (`UseCase`, `Port`,
  `Adapter`, `Repository`).
- Antes de adicionar um tipo em `model/` (raiz, fora de `domain/`), confirmar que será consumido
  por **2 ou mais domínios** — hoje só `PageResult` cumpre esse critério (6 domínios). Um tipo
  usado por um único domínio, mas também por um helper transversal de `utils/`, vai em
  `utils/model/` (ex.: `CacheEntry`), não em `domain/<x>/model/` — assim o helper continua sem
  depender de tipos de domínio.
- Qualquer novo client de integração externa deve embrulhar falhas em `IntegrationException` no
  `*Service` chamador — nunca deixar exceção de REST client/processo externo escapar crua até o
  `*Resource`.
- Chamadas a serviços externos com rate limit conhecido (AniList, TMDB) devem usar
  `RetryUtils.withRateLimitRetry` no ponto de chamada — hoje essa proteção é ad hoc; ao tocar em
  qualquer fluxo que chame `anilist`/`tmdb`, considerar se o call site precisa dessa proteção em vez
  de assumir que já existe.
- Se o domínio `cache` for revisado, considerar separar explicitamente a responsabilidade de fila
  de download de vídeo (`VideoFileCache`) da responsabilidade de cache de resposta de integração
  (`IntegrationCache`) — hoje convivem no mesmo pacote sem relação funcional.
- Ao alterar a assinatura de qualquer método de `TmdbService` (renomeado de `TmdbSearchService`
  nesta curadoria, exatamente por ser consumido service-a-service bem além da busca) usado fora do
  seu próprio `resource/`, avaliar o impacto em `anime`, `anibridge` e `ingestion`.
- Ao alterar `EpisodeNumberExtractor` (regex calibrada empiricamente) ou
  `AnibridgeResolutionService` (critério de candidato "suspeito"), tratar como código de alto risco
  de regressão silenciosa — testar manualmente contra casos reais antes de assumir correção.
- Manter o contrato (`openapi.yaml`) como fonte da verdade das descrições de tag; ao corrigir as
  divergências já identificadas aqui (menção a SQLite, SSE de notificação inexistente, "múltiplas
  páginas" do TMDB), atualizar a descrição da tag junto com a correção/confirmação do código, para
  este documento e o contrato não voltarem a divergir.
- Não reabrir a investigação do trade-off conhecido do `checkConnection()` do MEGA/rclone — é
  aceito; qualquer necessidade de health-check mais preciso para MEGA deve vir de requisito de
  produto novo, não de "corrigir" esse comportamento.
