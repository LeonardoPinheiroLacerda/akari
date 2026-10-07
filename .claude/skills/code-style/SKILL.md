---
name: code-style
description: Convenções de code style deste projeto. Trigger em "code style", "convenções", "estilo", "padrão de código", "como o projeto faz X", ou sempre que estiver escrevendo código Java novo. Cobre estrutura de pacotes, naming, injeção CDI, tipos, formatação e o que não usar.
---

# code-style

Padrão deste projeto: **um único módulo Quarkus, em camadas simples, contract-first** — o
contrato `src/main/resources/META-INF/openapi.yaml` gera as interfaces JAX-RS e os DTOs. Ao
criar arquivo novo, seguir tudo aqui. Ao editar arquivo existente, respeitar o estilo local se
divergir do skill — mas se for divergência grande, avisar o usuário antes de propagar.

A arquitetura é deliberadamente simples (e mais acoplada do que uma arquitetura hexagonal/
multi-módulo tradicional, com um UseCase por operação): uma funcionalidade nova deve mexer em
poucos arquivos. Não reintroduza ports, UseCases, adapters nem camadas de domínio paralelas.

## Regras invioláveis

- **Nunca** injetar por construtor. Sempre `@Inject` em campo.
- **Nunca** deixar `@Inject` inline (`@Inject Foo bar;` na mesma linha). Sempre em duas linhas, com linha em branco entre múltiplas injeções.
- **Nunca** remover o sufixo da classe no nome do campo injetado. `PedidoService pedidoService` — não `pedidos` nem `service`.
- **Nunca** usar `var` para type inference. Sempre declarar o tipo explícito.
- **Nunca** usar `import static`. Prefira qualificar (`MediaType.APPLICATION_JSON` no lugar de `APPLICATION_JSON`).
- **Nunca** escrever ternário que duplica tokens dos dois lados (`x + y ? a : x + z`). Use `if` com early return.
- **Nunca** usar string literal onde há constante (`"application/json"` — use `MediaType.APPLICATION_JSON`).
- **Nunca** escrever DTO de API à mão — eles são gerados do contrato.
- **Nunca** aninhar um `record`/classe dentro de outro tipo. Cada tipo — incluindo records
  auxiliares como um par de retorno ou um agrupamento de parâmetros — vai no próprio arquivo.
- **Sempre** javadoc em português.
- **Sempre** `final` em variáveis locais.

## Estrutura de pacotes

Todo domínio mora dentro de `domain/`, um pacote por domínio, com **subpacote por tipo de
componente** dentro de cada um. Nunca uma classe solta na raiz do pacote de domínio. Fora de
`domain/`, só o que é transversal — `domain` nunca compete com `exceptions`/`config`/`utils`/
`model` por ser mais um "pacote de tipo", ele é o espaço reservado pros domínios de negócio.

```
src/main/java/com/example/app/
├── domain/
│   ├── cliente/
│   │   ├── model/Cliente.java          # entidade JPA/Panache (active record)
│   │   ├── resource/ClienteResource.java  # implementa a interface *Api gerada do contrato
│   │   ├── service/ClienteService.java    # regra de negócio
│   │   └── mapper/ClienteMapper.java      # MapStruct: model ↔ DTO gerado
│   ├── pagamento/
│   │   ├── resource/PagamentoResource.java
│   │   ├── service/PagamentoService.java
│   │   ├── mapper/PagamentoMapper.java
│   │   └── client/              # REST client do sistema externo
│   │       ├── PagamentoClient.java
│   │       └── dtos/            # records da resposta do upstream
│   └── entrega/
│       ├── model/Entrega.java
│       ├── resource/EntregaResource.java
│       ├── service/EntregaService.java
│       ├── mapper/EntregaMapper.java
│       └── provider/            # detalhe de implementação dos providers de entrega (não é domínio)
│           ├── client/impl/     #   LocalEntregaClient, ExternalEntregaClient
│           ├── config/
│           ├── mapper/
│           ├── model/
│           └── processor/
├── model/
│   └── PageResult.java   # tipo genérico cross-domínio, sem dono de domínio
├── exceptions/    # AppException e subclasses
│   └── mapper/    #   ExceptionMappers → ApiError (+ ApiErrorResponses)
├── config/        # configuração transversal: health checks, @ConfigMapping, producers
└── utils/         # helpers puros, sem estado, usados por mais de um domínio

target/generated-sources/openapi/.../app/api/       # interfaces JAX-RS geradas (*Api)
target/generated-sources/openapi/.../app/api/dto/   # DTOs gerados (ApiError, *Response, ...)
```

- Subpacote por tipo **sempre singular**: `model/`, `resource/`, `service/`, `mapper/`,
  `client/`. Classe na raiz do pacote de domínio (`domain/cliente/Cliente.java`) é erro —
  toda classe do domínio mora no subpacote do seu tipo (`domain/cliente/model/Cliente.java`).
- `client/` só existe no domínio que integra sistema externo, e separa os records de
  request/response do upstream em `client/dtos/` (nome do upstream, ex.: `PagamentoChargeResponse`
  — não são DTOs da nossa API).
- Domínio com implementação de client mais complexa (múltiplos providers, configs por provider)
  usa um subpacote próprio pra isso (ex.: `entrega/provider/`) — isso **não** é subpacote
  por domínio, é separação por responsabilidade dentro do mesmo domínio.
- `exceptions/`, `config/`, `utils/` e `model/` são espaço comum, **não** domínio — não entram em
  `domain/` e não têm subpacote por tipo (são eles mesmos o "tipo").
- `model/` (raiz, fora de `domain/`) é o lugar pra tipo genérico sem dono de domínio único — hoje
  só `PageResult`, mas é onde entram outros tipos cross-domínio que surgirem. Novo tipo aqui só
  com motivo real (usado por 2+ domínios); na dúvida, deixe no domínio que o criou primeiro.
- Fluxo de uma requisição: `*Resource` → `*Service` → `model` (Panache) e/ou `client`.
  O resource é uma camada burra, sem orquestração: só delega pro service e devolve o retorno.
  Quem converte entrada/saída com o `*Mapper` é o **service** (injeta o mapper, recebe/devolve
  DTO gerado nas bordas do método).
- Service pode injetar outro service de outro domínio quando a regra atravessa domínios (ex.: o
  service de `entrega` usa o de `cliente`) — importa normalmente, já que agora são pacotes
  diferentes. Nunca acesse `model` de outro domínio pelo resource — passe pelo service.
- Domínio novo é um pacote novo dentro de `domain/`, sem avisar antes (é o caso comum). Pacote
  transversal novo (irmão de `domain`, `exceptions`, `config`, `utils`, `model`) só com motivo
  real — avise o usuário antes de criar.

## Sufixos e naming de classes

| Sufixo | Pacote | Papel |
|---|---|---|
| `*Resource` | `domain/<domínio>/resource/` | implementa uma interface `*Api` gerada (uma por tag do contrato) |
| `*Service` | `domain/<domínio>/service/` | regra de negócio de um domínio, `@ApplicationScoped` |
| (sem sufixo) | `domain/<domínio>/model/` | entidade Panache — nome do conceito: `Pedido`, `Cliente` |
| `*Mapper` | `domain/<domínio>/mapper/` | MapStruct (`@Mapper(componentModel = "cdi")`) |
| `*Client` | `domain/<domínio>/client/` | REST client (`@RegisterRestClient`) |
| `*Exception` | `exceptions/` | subclasse de `AppException`, fixa status + `ApiErrorCode` |
| `*ExceptionMapper` | `exceptions/mapper/` | `@ServerExceptionMapper` → `ApiError`, uma classe por tipo de erro |
| `*HealthCheck` | `config/` | `@Liveness`/`@Readiness` do SmallRye Health |

**Models não levam sufixo `Entity`**: o model é o conceito do domínio. Os DTOs gerados já têm
sufixo (`PedidoResponse`, `PedidoRequest`), então não há colisão de nome.

**Um service por responsabilidade, não por domínio.** O normal é um service por domínio
(`PedidoService`, `ClienteService`, `EntregaService`), com um método por operação, nomeado pelo
verbo: `list`, `get`/`find`, `create`, `update`, `delete`, `sync`, ... Mas quando um domínio tem
**múltiplos resources cobrindo facetas bem distintas do mesmo model** (ex.: um domínio com
resource de CRUD, outro de grafo de relações e outro de sincronização com um sistema externo —
todos sobre a mesma entidade), cada resource pode ter o seu próprio service, todos no
`domain/<domínio>/service/`, compartilhando o `model/` do domínio. Isso evita tanto um service
inchado com responsabilidades não relacionadas quanto fragmentar o domínio em pacotes artificiais
só pra manter "1 service". Continua 1:1 — um service por resource, nunca um service chamando
outro service do mesmo domínio pra driblar a regra. Não crie interface para o service — é sempre
uma classe concreta.

## Naming de campos `@Inject`

**Regra:** o nome do campo é o **nome da classe em camelCase, com o sufixo preservado**.

```java
// certo
@Inject
PedidoService pedidoService;

@Inject
PedidoMapper pedidoMapper;

// errado — sufixo removido
@Inject
PedidoService pedidos;

// errado — inline
@Inject PedidoService pedidoService;

// errado — nome genérico quando a classe é descritiva
@Inject
PedidoService service;
```

**Linha em branco entre múltiplas injeções:**

```java
@Inject
PedidoService pedidoService;

@Inject
ClienteService clienteService;

@Inject
PedidoMapper pedidoMapper;
```

## Models (entidades Panache, active record)

- Estendem `PanacheEntityBase` (não `PanacheEntity` — controlamos o `@GeneratedValue`).
- `@Id @GeneratedValue(strategy = IDENTITY) public Integer id;`
- Campos são `public` (o Panache faz o get/set virtual via bytecode enhancement).
- `@CreationTimestamp` no `createdAt` (imutável via `updatable = false`, sem `insertable = false`).
- `@ManyToOne(fetch = LAZY)` para relacionamentos; o banco é um só, então use FK de verdade.
- **Consultas moram na própria entidade**, como métodos estáticos com nome de negócio:

```java
public static Optional<Pedido> findByClienteAndStatus(Integer clienteId, String status) {
    return find("clienteId = ?1 and status = ?2", clienteId, status).firstResultOptional();
}
```

- O service chama `Pedido.findByClienteAndStatus(...)`, `pedido.persist()`,
  `Pedido.deleteById(id)` — não existe camada de repository.
- Enums persistidos ficam no `model/` do domínio; o `*Mapper` converte para o enum gerado do
  contrato (o MapStruct mapeia enums de mesmo nome sozinho).
- Nunca injetar `EntityManager` diretamente — use a API do Panache.

## Services

- `@ApplicationScoped`, um por resource (normalmente um por domínio — ver exceção em "Sufixos e
  naming de classes").
- Recebem e devolvem **DTO gerado do contrato** nas bordas do método (parâmetro de entrada e
  retorno) — o service injeta o `*Mapper` e converte model ↔ DTO internamente. O resource não
  vê model nenhum.
- `@Transactional` nos métodos que escrevem.
- Sinalizam erro lançando a `AppException` adequada (`ResourceNotFoundException`,
  `BusinessRuleException`, `IntegrationException`, `IntegrationNotConfiguredException`).
- Embrulham falhas de `clients` em `IntegrationException` — exceção de REST client nunca escapa
  crua.
- Tarefas `@Scheduled` ficam no service do domínio a que pertencem.
- `find*` devolve `Optional<T>` quando "não achou" é resultado legítimo; `get*` lança
  `ResourceNotFoundException`.

## Resources

- Contract-first: o resource **implementa a interface `*Api` gerada** (`app.api`), uma por tag
  do contrato. `@Path`, verbo, `@Produces`/`@Consumes`, `@ResponseStatus` (ex.: `201`) e Bean
  Validation (`@Valid`, `@NotNull`) já vêm da interface — o resource não repete nenhuma anotação
  JAX-RS.
- Nomes dos métodos, parâmetros e tipos de retorno vêm do `operationId` e dos schemas do contrato.
- Retorna o DTO gerado direto (não `Response`): o status de sucesso está no contrato.
- **Camada burra: só delega.** Injeta o `*Service` e chama o método correspondente, passando os
  parâmetros/DTO recebidos e devolvendo o que o service retornar — sem tradução, sem chamar
  `*Mapper`, sem nenhuma lógica. Toda orquestração (mapper + regra de negócio) é do service.
- Erros: lance `AppException` (ou deixe o service lançar). Nunca monte `Response` de erro no
  resource.

## Mappers

- MapStruct, `@Mapper(componentModel = "cdi")`, injetados por `@Inject` **no `*Service`** (nunca
  no resource).
- Convertem model ↔ DTO gerado e resposta de client → model. Um mapper por domínio
  (`PedidoMapper`), não um por par de tipos.
- Sem lógica de negócio: só cópia de campos, renomes (`@Mapping`) e conversões triviais.

## Clients

- `@RegisterRestClient(configKey = "<sistema>")`, URL em `quarkus.rest-client.<sistema>.url`.
- Os records de request/response do upstream ficam em `domain/<domínio>/client/dtos/`
  (`domain/pagamento/client/dtos/`), com o nome do upstream (`PagamentoChargeResponse`) — não são
  DTOs da nossa API. O `*Client` (a interface `@RegisterRestClient`) fica direto em
  `domain/<domínio>/client/`, fora do `dtos/`.
- Não tratam erro nem fazem retry: quem chama (o service) embrulha em `IntegrationException`.
  Fault tolerance (`@Retry`, `@Timeout`) vai no método do service, se precisar.

## Exceptions

- Exceções da aplicação estendem `AppException`, que carrega o status HTTP e o `ApiErrorCode`
  (enum gerado do contrato). Subclasse nova só quando surgir um status/código novo.
- Os `*ExceptionMapper` (`@ServerExceptionMapper`) ficam em `exceptions/mapper/`, um por tipo de
  erro, e montam a resposta pelo `ApiErrorResponses` (utilitário do mesmo subpacote). O corpo é
  sempre o `ApiError` do contrato.
- `exceptions/` não depende de `exceptions/mapper/`: no Javadoc das exceções, cite os mappers
  com `{@code}`, não `{@link}` (o link exigiria o import e criaria dependência circular).

## DTOs da API

- **Gerados, nunca escritos à mão.** Saem do contrato para `app.api.dto`, com Bean Validation
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
public List<Pedido> list(
        Integer page,
        Integer size,
        String status,
        Integer clienteId) {
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
return Pedido
        .<Pedido>find("clienteId", clienteId)
        .stream()
        .map(pedido -> pedido.status)
        .toList();
```

### Blank lines dentro de métodos

Separar blocos lógicos com **1 linha em branco**: (1) declarações/lookups iniciais, (2) trabalho
principal, (3) retorno.

```java
@Transactional
public Pedido update(Integer id, Pedido changes) {
    final Pedido pedido = get(id);

    pedido.clienteId = changes.clienteId;
    pedido.status = changes.status;
    pedido.total = changes.total;
    pedido.observacao = changes.observacao;

    return pedido;
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
callers: agrupe num `record` em arquivo próprio, no mesmo pacote do service que o usa
(`PedidoFilter`, `PedidoChanges`) — nunca aninhado na classe. Com 1-3 parâmetros, passe
solto.

## `@Transactional`

- Nos métodos do `service/` do domínio que escrevem.
- Nunca em resource, mapper, client ou model.

## Language

- **Javadoc em português.** Verbo no presente, frase curta.
- **Primeira sentença é resumo** — cabe em uma linha, descreve o que a peça é.
- **`@link` sem FQN.** Importe o tipo e referencie pelo nome simples.

## Reject

- Constructor injection (`@Inject public XxxService(...)` — proibido; use campo).
- `@Inject` inline (`@Inject Foo bar;`).
- Sufixo de classe removido do nome do campo (`pedidos` no lugar de `pedidoService`).
- `var` para type inference.
- `import static`.
- Ternário duplicando tokens dos dois lados.
- String literal onde constante existe (`"application/json"`).
- Wildcard imports (`import java.util.*;`).
- `record`/classe aninhada dentro de outro tipo (ex.: um record de retorno declarado dentro de
  uma classe utilitária ou de um service) — cada tipo vai no próprio arquivo.
- Reintroduzir a arquitetura antiga: interfaces `*UseCase`/`*Port`, `*Adapter`, `*Repository`/
  `*DataProvider` em cima do Panache, ou modelo de domínio paralelo à entidade.
- Classe solta na raiz do pacote de domínio (ex.: `domain/cliente/Cliente.java`) — tem que
  estar no subpacote de tipo (`domain/cliente/model/Cliente.java`).
- Domínio fora de `domain/`, ou pacote transversal (`exceptions`/`config`/`utils`/`model`) tratado
  como domínio (ganhando subpacote `model/resource/service/mapper`).
- DTO da API escrito à mão (é gerado do contrato).
- Resource recebendo/devolvendo model, chamando `*Mapper`, ou fazendo qualquer tradução —
  resource é camada burra, isso é do service.
- Regra de negócio no resource ou no mapper.
- `Response` montada no resource para erro (lance `AppException`).
- Resource repetindo anotações JAX-RS que já estão na interface gerada.
- Exceção de REST client escapando do service sem virar `IntegrationException`.
- `Collection<T>` em assinaturas (usar `List<T>` ou `Set<T>` explícito).
- `EntityManager` injetado diretamente.
- Campos de entidade `private` (o Panache active record precisa deles `public`).
- Javadoc em inglês.
- `insertable = false` num campo com default no banco (o Hibernate não relê; use
  `@CreationTimestamp` ou preencha em Java).

## Checklist para arquivo novo

- [ ] Pacote `domain/<domínio>` certo, subpacote de tipo certo (`model`, `resource`, `service`,
      `mapper`, `client` ou `client/dtos`), nada solto na raiz do domínio. Transversal
      (`exceptions`, `exceptions/mapper`, `config`, `utils`, `model` na raiz) só quando não
      pertence a nenhum domínio.
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
