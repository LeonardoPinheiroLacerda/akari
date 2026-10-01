---
name: write-javadoc
description: Escreve Javadoc em português para classes, interfaces, records, enums e métodos do projeto akari (resources, services, models, mappers, clients, exceptions, config). Trigger em "documentar", "javadoc", "adicionar documentação", "documenta esse pacote", "escreve javadoc". Cobre argumentos, retornos e exceptions com o motivo pelo qual são lançados. Ao final, faz uma auditoria de cobertura.
---

# write-javadoc

Gera Javadoc em português para o código do akari. Foco: um dev que entra no projeto precisa entender o que cada peça faz **só olhando os nomes e os Javadocs**, sem precisar ler o corpo dos métodos.

## Regras invioláveis

- **Português direto, sem enfeite**. Frases curtas, verbos no presente.
- **Primeira sentença é o resumo** (convenção Javadoc). Deve caber em uma linha e explicar em uma frase o que é/o que faz.
- **Todo método público/protected declara** `@param`, `@return` (se não for `void`) e `@throws`. Se um dos três não se aplica, omite — não escreva "n/a".
- **Nunca use `{@inheritDoc}`**. Todo `@Override` recebe Javadoc próprio, com `@param`/`@return`/`@throws` completos. A doc do override deve descrever **o que essa impl faz** (ex.: "delega ao client interno e mapeia o resultado"), não repetir a doc da interface. Isso obriga o dev a entender o que a impl adiciona de fato, e evita confusão sobre "herdou o quê?" quando alguém lê só a classe.
- **`@throws` explica quando E por quê**. Não é só listar o tipo; é descrever a condição de disparo.
- **Cada método é auto-suficiente**. Nunca escreva "overload de conveniência", "veja tal método", "similar ao anterior" — o leitor pode estar olhando SÓ este método. Descreva o comportamento completo (o que faz, quando cada param entra em jogo, o que retorna, o que joga). Se dois overloads têm a mesma semântica, isso é lugar pra explicar a diferença de assinatura de forma singular em cada um (ex.: "aceita {@code JavaType} pra tipos com generics" num, "aceita {@link Class} pra tipos concretos" no outro), não pra remeter um ao outro.
- **Records**: `@param` no Javadoc de classe, um por campo. Não escreva Javadoc nos accessors gerados (são triviais).
- **Nunca duplique o nome no texto**. Um método `public boolean isAlive()` NÃO precisa começar com "Verifica se está vivo" — comece com o *comportamento observável* ("Retorna se o rcd está atendendo requisições").
- **Referencie tipos com `{@link}`** quando outra classe/interface é central ao entendimento. Não abuse — usa quando adiciona valor.
- **`{@link}` só com nome simples** — nunca use FQN dentro do `{@link}`. Se o tipo já está importado (uso no código) ou é do mesmo pacote, usa `{@link SimpleName}`. Se não está importado E não é do mesmo pacote, três opções: (1) import de verdade se o tipo já vai ser referenciado no código; (2) **import "fantasma" só pra Javadoc é permitido** quando o click-through vale a pena — IntelliJ conta como uso, o rename mantém sincronia; (3) `{@code SimpleName}` quando nenhum dos dois se aplica ou se linters agressivos vão remover. **NÃO** deixe FQN dentro do `{@link}` — é feio e ilegível.
- **Não descreva o óbvio**. `@param id identificador` é ruim. `@param id identificador do provider a atualizar` é bom.
- **Não documente código gerado**. As interfaces JAX-RS (`akari.api`) e os DTOs (`akari.api.dto`) saem do contrato `src/main/resources/META-INF/openapi.yaml` para `target/generated-sources` — a documentação deles é a `description` no contrato. Se a descrição de um endpoint ou campo está ruim, corrija no `openapi.yaml`.

## O que documentar em cada tipo de arquivo

| Tipo | Nível de classe | Membros |
|---|---|---|
| **Service** | Domínio que cobre + regras principais | Cada método público: o que faz, `@Transactional` se escreve, exceções que lança |
| **Model (entidade Panache)** | Que tabela mapeia + o conceito de negócio | Comentário curto por campo; cada consulta estática com `@param`/`@return` |
| **Mapper (MapStruct)** | Que tipos converte (model ↔ DTO gerado, resposta de client → model) | Métodos com mapeamento não óbvio (`@Mapping`, conversão) |
| **Record (resposta de client, parâmetros agrupados)** | Contexto de uso + finalidade | `@param` por campo no Javadoc de classe |
| **Enum** | O que os valores representam no negócio | Métodos como qualquer classe |
| **Utilitário estático** | Por que é estático (pura, sem estado) + quando usar | Cada `public static` completo |
| **Anotação** | O que marcar, o que a marca implica, quem processa | (retenção/target não precisa) |
| **REST Resource** | Que interface gerada implementa, tradução HTTP ↔ service | Cada override: verbo + path, status de sucesso, exceções que viram 4xx/5xx |
| **Exception / ExceptionMapper** | Quando é lançada e que status/código vira (`AkariException`) — ou que exceção o mapper traduz | O status e o `ApiErrorCode` devolvidos |
| **REST Client (MicroProfile)** | Que serviço externo mapeia + como resolver URL | Cada método: endpoint chamado + entradas/saídas |

## Template por tipo

### Service

```java
/**
 * Regras de <domínio>: <o que o service cobre em uma frase>.
 *
 * <p><Regras não óbvias que valem para vários métodos, outros services ou clients que usa.>
 */
@ApplicationScoped
public class XxxService {

    /**
     * <O que a operação faz, focando o comportamento observável>.
     *
     * <p><Fluxo, se não for óbvio: passo 1 → passo 2. Mencione @Transactional e por quê.>
     *
     * @param id <significado + qualquer restrição>
     * @return <o que vem + quando>
     * @throws ResourceNotFoundException se <condição exata>
     * @throws IntegrationException se <sistema externo> falhar ao <ação>
     */
    @Transactional
    public Xxx update(Integer id, Xxx changes) { ... }
}
```

### Record (resposta de client ou parâmetros agrupados)

```java
/**
 * <O que este record representa e onde é usado>.
 *
 * <p><Opcional: contexto adicional>.
 *
 * @param field1 <o que é, restrições, quem preenche/quem lê>
 * @param field2 <o que é, restrições, quem preenche/quem lê>
 */
public record TmdbMovieSearchResponse(FieldType1 field1, FieldType2 field2) {}
```

### Utilitário estático

```java
/**
 * <O que faz em uma frase>.
 *
 * <p><Por que é estático: função pura, sem estado, sem dependências. Quando usar.>
 */
public final class Xxx {

    private Xxx() {}

    /**
     * <Comportamento observável>.
     *
     * <p><Efeitos colaterais explícitos: muta o arg? quando ignora?>
     *
     * @param foo <...>
     * @return <...>
     */
    public static ReturnType apply(ParamType foo) { ... }
}
```

### REST Resource

O akari é contract-first: o resource implementa a interface gerada do contrato (`XxxApi` em
`akari.api`), que já traz `@Path`, verbo, `@ResponseStatus` e Bean Validation. O resource não
repete anotações JAX-RS — só implementa os métodos.

```java
/**
 * Implementa {@link XxxApi} — endpoints de <domínio> em {@code /v1/base/path}.
 *
 * <p>Só traduz HTTP ↔ service, convertendo com o {@link XxxMapper}. Bean Validation dos DTOs
 * gerados vira 400; exceções da aplicação ({@code AkariException}) viram o status/código de
 * cada uma pelos mappers de {@code exceptions.mapper}.
 */
public class XxxResource implements XxxApi {

    /**
     * {@code GET /v1/base/path/{id}} — <o que faz do ponto de vista do consumidor da API>.
     *
     * @param id <significado no contexto do endpoint>
     * @return <o que vem no corpo>, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se <condição>
     */
    @Override
    public XxxResponse getXxx(Integer id) { ... }
}
```

### REST Client (MicroProfile)

```java
/**
 * REST Client (MicroProfile) que mapeia os endpoints do <serviço externo>.
 *
 * <p>URL base resolvida por {@code quarkus.rest-client.<key>.url}. Não faz retry nem
 * tradução de exceções — o service que chama embrulha as falhas em {@code IntegrationException}.
 */
@RegisterRestClient(configKey = "xxx")
public interface XxxClient {

    /**
     * <O que o endpoint faz do ponto de vista da nossa API>.
     *
     * @param request <significado dos campos-chave>
     * @return <o que a resposta contém>
     */
    @POST
    @Path("/endpoint")
    XxxSearchResponse search(XxxSearchRequest request);
}
```

### Model (entidade Panache, active record)

```java
/**
 * <Conceito de negócio> persistido na tabela {@code xxx}.
 *
 * <p><Invariantes ou relações não óbvias.> Usado direto pelos services; o resource o converte
 * para o DTO do contrato via {@link XxxMapper}.
 */
@Entity
@Table(name = "xxx")
public class Xxx extends PanacheEntityBase {

    /** <Semântica curta do campo, incluindo como é gravado se não for óbvio.> */
    public Type field;

    /**
     * <O que a consulta devolve, em termos de negócio>.
     *
     * @param path <significado + restrição>
     * @return <o que vem>; vazio se <condição>
     */
    public static Optional<Xxx> findByPath(String path) { ... }
}
```

## Checklist ao terminar cada arquivo

Antes de fechar o arquivo, confira:

- [ ] Classe/interface/record tem Javadoc de classe.
- [ ] Todo método público/protected tem Javadoc.
- [ ] **Todo `@Override` tem Javadoc próprio completo** (sem `{@inheritDoc}`), descrevendo o que essa impl específica faz.
- [ ] Construtores com semântica não-trivial têm Javadoc.
- [ ] Cada `@param` explica **o que é** o parâmetro E qualquer restrição.
- [ ] `@return` diz **o que vem** e **quando** (não só o tipo).
- [ ] Cada `@throws` explica a **condição exata** que dispara.
- [ ] Records: `@param` por campo no Javadoc de classe.
- [ ] Referências entre classes usam `{@link}`.
- [ ] Não tem texto vazio nem "TODO" nem "resumo".

### Verificação automatizada de overrides

```bash
# @Override precisa ter `/**` ou `*/` na linha imediatamente anterior
for f in $(find src/main/java -name "*.java"); do
  awk 'prev !~ /\*\// && prev !~ /\/\*\*/ && /^\s*@Override/ {
    print FILENAME ":" NR ": @Override sem Javadoc"
  }
  { prev = $0 }' "$f"
done
```

## Fluxo recomendado ao documentar um domínio inteiro

O akari organiza o código por tipo de componente (`resources`, `services`, `models`, `mappers`,
`clients`, `exceptions`, `config`), então um domínio (ex.: catálogo) está espalhado por vários
pacotes: `MediaFolder` em `models`, `MediaFolderService` em `services`, `MediaFolderMapper` em
`mappers`, `MediaFoldersResource` em `resources`.

1. **Inventário**: `find src/main/java -name "*.java" | grep -i <termo-do-dominio> | sort`
   (ou o pacote inteiro, se a tarefa for por pacote).
2. **Tasks por grupo**: TaskCreate uma por grupo, marcadas `in_progress` conforme ataca.
3. **Ordem sugerida** — do vocabulário para as bordas:
   - `models` — dá o vocabulário do domínio
   - `services` — as regras de negócio
   - `clients` — o protocolo dos sistemas externos
   - `mappers` — as conversões
   - `resources` — a borda HTTP
   - `exceptions` / `config` — só se o domínio trouxe algo novo
4. **Auditoria** ao final:
   ```bash
   DIR=src/main/java/io/github/leonardopinheirolacerda/akari

   # todo arquivo tem pelo menos um /**
   find "$DIR" -name "*.java" | while read f; do
     grep -q "^\s*/\*\*" "$f" || echo "MISSING: $f"
   done

   # arquivos com métodos públicos sem @param/@return/@throws
   for f in $(find "$DIR" -name "*.java"); do
     n=$(grep -cE "^    public[a-zA-Z<> ]+\(" "$f")
     if [ "$n" -gt 0 ] && ! grep -qE "@param|@return|@throws" "$f"; then
       echo "MISSING tags: $f"
     fi
   done
   ```
5. **Valida as referências do Javadoc** — o `javac` não acusa `{@link}` para tipo inexistente; o
   javadoc com doclint acusa e falha o build:
   ```bash
   ./mvnw clean org.apache.maven.plugins:maven-javadoc-plugin:3.7.0:javadoc -Ddoclint=reference
   ```
   O `clean` é obrigatório: sem ele o plugin responde "everything is up to date" e não roda.
   A versão explícita do plugin permite rodar offline (`-o`).

## Anti-padrões (reject imediato)

- **"Este método serve para..."** — verboso e inútil. Comece direto pela ação.
- **"Retorna o resultado"** em `@return` — não diz nada.
- **Repetir o nome do método traduzido** — se o nome é `checkConnection`, `@return true se conectou` já basta; NÃO escreva "Verifica a conexão. @return...".
- **@throws sem condição** — "@throws IOException se der erro" é lixo. Diga *qual* erro.
- **`{@link fully.qualified.Name}`** — feio, ilegível, some no rendering. Sempre `{@link SimpleName}` com import (real ou fantasma), ou `{@code SimpleName}` sem link. Nunca inclua o pacote no `{@link}`.
- **`{@inheritDoc}`** — proibido. Cada override tem sua própria doc, descrevendo o que a impl faz.
- **"Overload de conveniência" / "veja o método acima" / "similar ao anterior"** — proibido. O leitor pode estar olhando SÓ esse método. Cada assinatura recebe doc auto-suficiente que descreve tudo (comportamento, params, retorno, exceções). Se dois overloads compartilham semântica, explique a diferença de assinatura em cada um sem apontar pro outro.
- **Copiar-colar Javadoc entre métodos diferentes**. Cada um tem contexto distinto.
- **Documentar internals** (private helpers pequenos). Só se tiver invariante escondida.
- **Emojis, exclamações, primeira pessoa**. Isto não é rede social.
- **Deixar `TODO: doc`** em qualquer arquivo. Se não sabe descrever, PERGUNTE o que a peça faz antes de escrever.

## Exemplo lado-a-lado

**Ruim:**
```java
/**
 * Método que executa a criação.
 * @param body o body
 * @return o id
 */
public Integer create(CreateDto body) { ... }
```

**Bom:**
```java
/**
 * {@code POST /v1/storage/providers} — cadastra um novo provider.
 *
 * @param request tipo do provider + config em claro, já validados pelo Bean Validation do DTO
 *                gerado
 * @return provider recém-criado (sem {@code config}), com {@code 201 Created}
 * @throws BusinessRuleException (traduzida em 400) se a config não tiver as chaves exigidas
 *         pelo {@code type}
 * @throws IntegrationException (traduzida em 502) se o rclone rcd estiver fora ao obscurecer
 *         segredos
 */
@Override
public StorageProviderResponse createStorageProvider(CreateStorageProviderRequest request) { ... }
```

## Formatação

- Use `<p>` entre parágrafos dentro do Javadoc (senão HTML colapsa).
- Backticks em Markdown viram `{@code ...}` em Javadoc.
- Multi-linhas de `@throws` alinha a continuação com 2 espaços após a coluna do texto.
- Limite ~100 colunas por linha.
