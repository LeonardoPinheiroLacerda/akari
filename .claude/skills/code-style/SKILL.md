---
name: code-style
description: Convenções de code style do projeto akari. Trigger em "code style", "convenções", "estilo", "padrão de código", "como o projeto faz X", ou sempre que estiver escrevendo código Java novo. Cobre estrutura de pacotes, naming, injeção CDI, tipos, formatação e o que não usar.
---

# code-style

Padrão do akari: **um único módulo Quarkus, em camadas simples, contract-first** — o contrato
`src/main/resources/META-INF/openapi.yaml` gera as interfaces JAX-RS e os DTOs. Ao criar arquivo
novo, seguir tudo aqui. Ao editar arquivo existente, respeitar o estilo local se divergir do
skill — mas se for divergência grande, avisar o usuário antes de propagar.

A arquitetura é deliberadamente mais simples (e mais acoplada) que a do legado `akari-backend`
(multi-módulo, hexagonal, um UseCase por operação): uma funcionalidade nova deve mexer em
poucos arquivos. Não reintroduza ports, UseCases, adapters nem camadas de domínio paralelas.

## Regras invioláveis

- **Nunca** injetar por construtor. Sempre `@Inject` em campo.
- **Nunca** deixar `@Inject` inline (`@Inject Foo bar;` na mesma linha). Sempre em duas linhas, com linha em branco entre múltiplas injeções.
- **Nunca** remover o sufixo da classe no nome do campo injetado. `MediaFolderService mediaFolderService` — não `mediaFolders` nem `service`.
- **Nunca** usar `var` para type inference. Sempre declarar o tipo explícito.
- **Nunca** usar `import static`. Prefira qualificar (`MediaType.APPLICATION_JSON` no lugar de `APPLICATION_JSON`).
- **Nunca** escrever ternário que duplica tokens dos dois lados (`x + y ? a : x + z`). Use `if` com early return.
- **Nunca** usar string literal onde há constante (`"application/json"` — use `MediaType.APPLICATION_JSON`).
- **Nunca** escrever DTO de API à mão — eles são gerados do contrato.
- **Sempre** javadoc em português.
- **Sempre** `final` em variáveis locais.

## Estrutura de pacotes

Pacotes por **tipo de componente**, todos no primeiro nível de `io.github.leonardopinheirolacerda.akari`:

```
src/main/java/io/github/leonardopinheirolacerda/akari/
├── resources/     # *Resource — implementam as interfaces *Api geradas do contrato
├── services/      # *Service — regra de negócio, um por domínio
├── models/        # entidades JPA/Panache (active record) + enums persistidos
├── mappers/       # *Mapper — MapStruct: model ↔ DTO gerado, resposta de client → model
├── clients/       # REST clients de sistemas externos, um subpacote por sistema
│   └── tmdb/      #   ex.: TmdbClient + records da resposta do TMDB
├── exceptions/    # AkariException e subclasses
│   └── mapper/    #   ExceptionMappers → ApiError (+ ApiErrorResponses)
└── config/        # configuração transversal: health checks, @ConfigMapping, producers

target/generated-sources/openapi/.../akari/api/       # interfaces JAX-RS geradas (*Api)
target/generated-sources/openapi/.../akari/api/dto/   # DTOs gerados (ApiError, *Response, ...)
```

- Pacotes **sem subpacotes por domínio** (nada de `services/catalog/`). As exceções são
  `clients/`, que agrupa por sistema externo (`clients/tmdb`, `clients/anilist`, `clients/rclone`),
  e `exceptions/mapper/`, que separa os ExceptionMappers das exceções.
- Fluxo de uma requisição: `*Resource` → `*Service` → `models` (Panache) e/ou `clients`.
  O resource é uma camada burra, sem orquestração: só delega pro service e devolve o retorno.
  Quem converte entrada/saída com o `*Mapper` é o **service** (injeta o mapper, recebe/devolve
  DTO gerado nas bordas do método).
- Service pode injetar outro service quando a regra atravessa domínios (ex.: o service de
  ingestão usa o de catálogo). Nunca acesse `models` de outro domínio pelo resource — passe
  pelo service.
- Novo pacote de primeiro nível só com motivo real (ex.: `jobs/` se os `@Scheduled` deixarem de
  caber nos services). Avise o usuário antes de criar.

## Sufixos e naming de classes

| Sufixo | Pacote | Papel |
|---|---|---|
| `*Resource` | `resources/` | implementa uma interface `*Api` gerada (uma por tag do contrato) |
| `*Service` | `services/` | regra de negócio de um domínio, `@ApplicationScoped` |
| (sem sufixo) | `models/` | entidade Panache — nome do conceito: `MediaFolder`, `VideoFile` |
| `*Mapper` | `mappers/` | MapStruct (`@Mapper(componentModel = "cdi")`) |
| `*Client` | `clients/<sistema>/` | REST client (`@RegisterRestClient`) |
| `*Exception` | `exceptions/` | subclasse de `AkariException`, fixa status + `ApiErrorCode` |
| `*ExceptionMapper` | `exceptions/mapper/` | `@ServerExceptionMapper` → `ApiError`, uma classe por tipo de erro |
| `*HealthCheck` | `config/` | `@Liveness`/`@Readiness` do SmallRye Health |

**Models não levam sufixo `Entity`**: o model é o conceito do domínio. Os DTOs gerados já têm
sufixo (`MediaFolderResponse`, `MediaFolderRequest`), então não há colisão de nome.

**Um service por domínio** (`MediaFolderService`, `VideoFileService`, `CacheService`), com um
método por operação, nomeado pelo verbo: `list`, `get`/`find`, `create`, `update`, `delete`,
`sync`, ... Não crie interface para o service — é uma classe concreta.

## Naming de campos `@Inject`

**Regra:** o nome do campo é o **nome da classe em camelCase, com o sufixo preservado**.

```java
// certo
@Inject
MediaFolderService mediaFolderService;

@Inject
MediaFolderMapper mediaFolderMapper;

// errado — sufixo removido
@Inject
MediaFolderService mediaFolders;

// errado — inline
@Inject MediaFolderService mediaFolderService;

// errado — nome genérico quando a classe é descritiva
@Inject
MediaFolderService service;
```

**Linha em branco entre múltiplas injeções:**

```java
@Inject
MediaFolderService mediaFolderService;

@Inject
VideoFileService videoFileService;

@Inject
MediaFolderMapper mediaFolderMapper;
```

## Models (entidades Panache, active record)

- Estendem `PanacheEntityBase` (não `PanacheEntity` — controlamos o `@GeneratedValue`).
- `@Id @GeneratedValue(strategy = IDENTITY) public Integer id;`
- Campos são `public` (o Panache faz o get/set virtual via bytecode enhancement).
- `@CreationTimestamp` no `createdAt` (imutável via `updatable = false`, sem `insertable = false`).
- `@ManyToOne(fetch = LAZY)` para relacionamentos; o banco é um só, então use FK de verdade.
- **Consultas moram na própria entidade**, como métodos estáticos com nome de negócio:

```java
public static Optional<MediaFolder> findByProviderAndPath(Integer providerId, String path) {
    return find("storageProviderId = ?1 and path = ?2", providerId, path).firstResultOptional();
}
```

- O service chama `MediaFolder.findByProviderAndPath(...)`, `folder.persist()`,
  `MediaFolder.deleteById(id)` — não existe camada de repository.
- Enums persistidos ficam em `models/`; o `*Mapper` converte para o enum gerado do contrato
  (o MapStruct mapeia enums de mesmo nome sozinho).
- Nunca injetar `EntityManager` diretamente — use a API do Panache.

## Services

- `@ApplicationScoped`, um por domínio.
- Recebem e devolvem **DTO gerado do contrato** nas bordas do método (parâmetro de entrada e
  retorno) — o service injeta o `*Mapper` e converte model ↔ DTO internamente. O resource não
  vê model nenhum.
- `@Transactional` nos métodos que escrevem.
- Sinalizam erro lançando a `AkariException` adequada (`ResourceNotFoundException`,
  `BusinessRuleException`, `IntegrationException`, `IntegrationNotConfiguredException`).
- Embrulham falhas de `clients` em `IntegrationException` — exceção de REST client nunca escapa
  crua.
- Tarefas `@Scheduled` ficam no service do domínio a que pertencem.
- `find*` devolve `Optional<T>` quando "não achou" é resultado legítimo; `get*` lança
  `ResourceNotFoundException`.

## Resources

- Contract-first: o resource **implementa a interface `*Api` gerada** (`akari.api`), uma por tag
  do contrato. `@Path`, verbo, `@Produces`/`@Consumes`, `@ResponseStatus` (ex.: `201`) e Bean
  Validation (`@Valid`, `@NotNull`) já vêm da interface — o resource não repete nenhuma anotação
  JAX-RS.
- Nomes dos métodos, parâmetros e tipos de retorno vêm do `operationId` e dos schemas do contrato.
- Retorna o DTO gerado direto (não `Response`): o status de sucesso está no contrato.
- **Camada burra: só delega.** Injeta o `*Service` e chama o método correspondente, passando os
  parâmetros/DTO recebidos e devolvendo o que o service retornar — sem tradução, sem chamar
  `*Mapper`, sem nenhuma lógica. Toda orquestração (mapper + regra de negócio) é do service.
- Erros: lance `AkariException` (ou deixe o service lançar). Nunca monte `Response` de erro no
  resource.

## Mappers

- MapStruct, `@Mapper(componentModel = "cdi")`, injetados por `@Inject` **no `*Service`** (nunca
  no resource).
- Convertem model ↔ DTO gerado e resposta de client → model. Um mapper por domínio
  (`MediaFolderMapper`), não um por par de tipos.
- Sem lógica de negócio: só cópia de campos, renomes (`@Mapping`) e conversões triviais.

## Clients

- `@RegisterRestClient(configKey = "<sistema>")`, URL em `quarkus.rest-client.<sistema>.url`.
- Os records da resposta upstream ficam no mesmo subpacote do client (`clients/tmdb/`), com o
  nome do upstream (`TmdbMovieSearchResponse`) — não são DTOs da nossa API.
- Não tratam erro nem fazem retry: quem chama (o service) embrulha em `IntegrationException`.
  Fault tolerance (`@Retry`, `@Timeout`) vai no método do service, se precisar.

## Exceptions

- Exceções da aplicação estendem `AkariException`, que carrega o status HTTP e o `ApiErrorCode`
  (enum gerado do contrato). Subclasse nova só quando surgir um status/código novo.
- Os `*ExceptionMapper` (`@ServerExceptionMapper`) ficam em `exceptions/mapper/`, um por tipo de
  erro, e montam a resposta pelo `ApiErrorResponses` (utilitário do mesmo subpacote). O corpo é
  sempre o `ApiError` do contrato.
- `exceptions/` não depende de `exceptions/mapper/`: no Javadoc das exceções, cite os mappers
  com `{@code}`, não `{@link}` (o link exigiria o import e criaria dependência circular).

## DTOs da API

- **Gerados, nunca escritos à mão.** Saem do contrato para `akari.api.dto`, com Bean Validation
  aplicada a partir do schema (`required`, `minLength`, `minimum`, ...).
- Mudou o payload? Edite o schema no contrato e regenere — não crie record paralelo.

## Tipos

| Situação | Tipo |
|---|---|
| Id de entidade (parâmetro, campo, componente de record) | `Integer` |
| Página, tamanho de página, offset, número de temporada/episódio, etc. | `Integer` |
| Contadores puramente locais (loop counters, acumuladores dentro de método) | `int` primitivo |
| Constantes numéricas privadas (`private static final ...`) | `int` primitivo |
| Tamanhos em bytes, timestamps unix | `Long` |
| Datetime | `LocalDateTime` |
| Flag opcional (nullable com significado tri-state) | `Boolean` (wrapper) |
| Flag interna determinística | `boolean` primitivo |
| Retorno de busca que pode legitimamente não achar | `Optional<T>` |
| Coleções em assinaturas | `List<T>`, `Set<T>`, `Map<K,V>` — nunca a interface `Collection<T>` genérica |

**Numéricos em assinaturas são wrappers.** IDs, `page`, `size`, `status`, etc. — tudo `Integer`
em parâmetros de método, componentes de record e campos. Cuidado com null unboxing dentro de
método; a integridade fica na fronteira REST, via Bean Validation e `default` gerados do contrato.

**Primitivos `int` só como detalhe de implementação:** locais de loop, contadores efêmeros
dentro de um método, constantes `private static final`. Nada disso cruza fronteira de método.

**Exceção legítima:** override de método de biblioteca externa que fixa o tipo primitivo na
assinatura (ex.: `ResponseExceptionMapper#handles(int status, ...)`).

## Formatação

### Imports

- Sem `import static`.
- Ordem alfabética simples é OK — segue o padrão da IDE (IntelliJ default).
- Zero imports com `.*` (wildcards).

### Método: quebras de linha

- Linha com no máximo ~100 colunas.
- Assinatura em uma linha se couber. Caso contrário, cada param numa linha:

```java
public List<MediaFolder> list(
        Integer page,
        Integer size,
        String name,
        Integer storageProviderId) {
    ...
}
```

- Construtor/chamada com muitos args: um por linha, `)` em linha própria:

```java
return ApiErrorResponses.of(
        400,
        ApiErrorCode.VALIDATION_ERROR,
        "Payload ou parâmetros inválidos",
        details,
        e
);
```

- Stream/method chains: cada operador numa linha:

```java
return MediaFolder
        .<MediaFolder>find("storageProviderId", storageProviderId)
        .stream()
        .map(folder -> folder.path)
        .toList();
```

### Blank lines dentro de métodos

Separar blocos lógicos com **1 linha em branco**: (1) declarações/lookups iniciais, (2) trabalho
principal, (3) retorno.

```java
@Transactional
public MediaFolder update(Integer id, MediaFolder changes) {
    final MediaFolder folder = get(id);

    folder.storageProviderId = changes.storageProviderId;
    folder.mediaType = changes.mediaType;
    folder.name = changes.name;
    folder.path = changes.path;

    return folder;
}
```

### Ternários

Só usar quando os dois braços são fundamentalmente diferentes e curtos:

```java
// certo
final String direction = ascending ? "asc" : "desc";

// errado — duplica tokens
folderPath.endsWith("/") ? folderPath + fileName : folderPath + "/" + fileName;
```

Se os dois lados repetem tokens, refatorar pra `if` com early return:

```java
if (folderPath.endsWith("/")) {
    return folderPath + fileName;
}
return folderPath + "/" + fileName;
```

### Métodos privados no fim da classe

Helpers privados ficam depois dos métodos públicos, na ordem em que aparecem sendo chamados
(leitura top-down).

## Parâmetros agrupados

Método de service com **4+ parâmetros** de escrita, ou com o mesmo shape montado por vários
callers: agrupe num `record` aninhado no próprio service (`MediaFolderService.Filter`,
`MediaFolderService.Changes`). Com 1-3 parâmetros, passe solto.

## `@Transactional`

- Nos métodos de `services/` que escrevem.
- Nunca em resource, mapper, client ou model.

## Language

- **Javadoc em português.** Verbo no presente, frase curta.
- **Primeira sentença é resumo** — cabe em uma linha, descreve o que a peça é.
- **`@link` sem FQN.** Importe o tipo e referencie pelo nome simples.

## Reject

- Constructor injection (`@Inject public XxxService(...)` — proibido; use campo).
- `@Inject` inline (`@Inject Foo bar;`).
- Sufixo de classe removido do nome do campo (`mediaFolders` no lugar de `mediaFolderService`).
- `var` para type inference.
- `import static`.
- Ternário duplicando tokens dos dois lados.
- String literal onde constante existe (`"application/json"`).
- Wildcard imports (`import java.util.*;`).
- Reintroduzir a arquitetura antiga: interfaces `*UseCase`/`*Port`, `*Adapter`, `*Repository`/
  `*DataProvider` em cima do Panache, ou modelo de domínio paralelo à entidade.
- Subpacote por domínio dentro de `services/`, `resources/`, `models/` ou `mappers/`.
- DTO da API escrito à mão (é gerado do contrato).
- Resource recebendo/devolvendo model, chamando `*Mapper`, ou fazendo qualquer tradução —
  resource é camada burra, isso é do service.
- Regra de negócio no resource ou no mapper.
- `Response` montada no resource para erro (lance `AkariException`).
- Resource repetindo anotações JAX-RS que já estão na interface gerada.
- Exceção de REST client escapando do service sem virar `IntegrationException`.
- `Collection<T>` em assinaturas (usar `List<T>` ou `Set<T>` explícito).
- `EntityManager` injetado diretamente.
- Campos de entidade `private` (o Panache active record precisa deles `public`).
- Javadoc em inglês.
- `insertable = false` num campo com default no banco (o Hibernate não relê; use
  `@CreationTimestamp` ou preencha em Java).

## Checklist para arquivo novo

- [ ] Pacote correto pelo tipo de componente (`resources`, `services`, `models`, `mappers`,
      `clients/<sistema>`, `exceptions`, `exceptions/mapper`, `config`).
- [ ] Sufixo da classe bate com o papel (models sem sufixo).
- [ ] Injeções em linhas separadas, com linha em branco entre elas.
- [ ] Nomes dos campos injetados preservam o sufixo da classe.
- [ ] Numéricos em assinaturas são `Integer`; `int` só em locais de loop e constantes privadas.
- [ ] `final` em todas as variáveis locais.
- [ ] Blocos lógicos separados por linha em branco; linhas com no máximo ~100 colunas.
- [ ] Chamadas com muitos args quebradas com um arg por linha.
- [ ] `@Transactional` só em método de service que escreve.
- [ ] Javadoc de classe presente; javadoc de método público com `@param`/`@return`/`@throws`.
- [ ] Nenhum FQN em `@link`.
