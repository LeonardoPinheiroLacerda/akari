---
name: logging
description: Adiciona logs (INFO/WARN/ERROR) no código do akari sem alterar o comportamento existente. Trigger em "adiciona log", "coloque logs", "log detalhado", "quais pontos podem ter log", ou sempre que for instrumentar um service/client/processor com logging.
---

# logging

Adiciona observabilidade ao akari sem reescrever a lógica que já existe. A regra de ouro: **log é
sempre uma adição em cima do código atual, nunca um motivo pra mudar sua forma**.

## Regra inviolável: log nunca muda o processo

- **Nunca crie `if`/`else if`/`else` só pra decidir o que logar.** Se o código já tem um branch
  (um `if` de validação, um `catch`), o log entra *dentro* dele. Se não tem branch, o log é uma
  linha única e incondicional — não vire o resultado em branch pra "logar diferente conforme o
  caso".

  ```java
  // errado — criou um if/else que só existe pro log
  if (!healthy) {
      Log.warnf("...");
  } else {
      Log.infof("...");
  }

  // certo — uma linha, incondicional, usa a variável que já existia
  Log.infof("Storage provider %d: healthy=%s", providerId, healthy);
  ```

- **Nunca adicione parâmetro em método só pra log.** Se o dado que você quer logar não é algo que
  o método já recebe, já calcula ou já tem como campo/variável local, ele não entra no log dessa
  passada — é sinal de que esse log pertence a outro lugar (normalmente uma camada acima, que já
  tem esse dado).

- **Nunca crie um `try/catch` novo só pra logar.** Log dentro de um `catch` que **já existia** é
  sempre bem-vindo (é um ponto de falha real, silencioso até então). Criar um `catch` que não
  existia, só pra ter onde logar, é mudar o processo — exatamente o que essa regra proíbe.

  **Única exceção aberta nessa sessão:** em `ObscureFieldApplier`, um `try/catch` novo foi
  adicionado ao redor da chamada ao rclone porque a falha ali era **genuinamente invisível** antes
  (exceção crua se propagando sem nenhum sinal) — foi tratado como melhoria pontual, não como
  precedente. Não repita esse padrão sem o usuário pedir de novo; by default, log só entra em
  branch/catch que já existia.

## Onde logar (em ordem de preferência)

1. **Dentro de um `catch` que já existe** — sempre ERROR (se é falha de infra/sistema externo) ou
   WARN (se é esperado, ex.: "não achei", "não está acessível"), com a exceção como primeiro
   argumento pra manter o stack trace: `Log.errorf(e, "mensagem com %s", valor)`.
2. **Dentro de um `if` que já existe** — normalmente WARN, pra sinalizar o caminho alternativo que
   esse `if` já tratava (ex.: "diretório não existe", "config inválida antes de lançar").
3. **Uma linha incondicional, logo antes/depois de uma ação que já existia** — normalmente INFO,
   pra marcar início/fim de uma operação (criar, remover, listar, resolver um client). Usa só
   parâmetros/variáveis que já estavam no escopo.
4. **Um `switch`/expressão existente** — prefira um log *antes* do switch, descrevendo o que vai
   ser decidido, em vez de transformar cada `case ->` numa `case -> { ... }` só pra logar dentro.
   Mais simples e não toca na forma do switch.

## Qual nível usar

- **INFO**: a operação aconteceu como esperado — início de uma operação de escrita (criar,
  atualizar, remover), resultado de uma consulta, resolução de um client/estratégia. Não é
  "sucesso" vs "erro", é "isso rodou, aqui está o resultado" — por isso `healthy=%s` vira um único
  INFO com o valor embutido, nunca dois logs (um de cada branch).
- **WARN**: dentro de um branch/catch que já existia e que trata um caminho **esperado mas
  degradado** — recurso não encontrado, provider inacessível, diretório ausente, validação que
  falhou antes de lançar. Não é bug nosso, é o sistema reagindo a uma condição real.
- **ERROR**: dentro de um `catch` que já existia e que trata uma falha de **infraestrutura ou
  sistema externo** (rclone fora do ar, IO inesperado) — sempre com a exceção original como
  primeiro argumento, nunca só a mensagem.

## Qual logger usar

**`io.quarkus.logging.Log`** (estático, do próprio Quarkus) — nunca Lombok (`@JBossLog`,
`@Slf4j`) nem `org.jboss.logging.Logger` manual com campo `LOG`/`log`. `Log.infof(...)`,
`Log.warnf(...)`, `Log.errorf(throwable, ...)` direto, sem injeção, sem campo, sem anotação na
classe — o Quarkus infere a categoria (nome da classe) via transformação de bytecode no build.

```java
import io.quarkus.logging.Log;

Log.infof("Criando storage provider do tipo %s", type);
Log.warnf("Storage provider %d não encontrado", providerId);
Log.errorf(e, "rclone rcd não está acessível ao verificar o storage provider %d", providerId);
```

## Mensagens

- **Português**, mesmo padrão do resto do projeto (exceptions, javadoc).
- Usa os placeholders `%s`/`%d` do `*f` (printf-style) do JBoss Logging por baixo — não concatena
  string.
- Inclui o identificador relevante que já estava disponível (id do provider, path, tipo) — não
  adiciona busca extra só pra enriquecer o log.

## O que NÃO fazer (resumo)

- `if`/`else` criado só pra escolher o nível/mensagem do log.
- Parâmetro novo num método só pra ter o que logar.
- `try/catch` novo só pra ter onde logar (exceto melhoria pontual, combinada com o usuário).
- Lombok `@JBossLog`/`@Slf4j`, ou `Logger.getLogger()` manual — sempre `io.quarkus.logging.Log`.
- Log em inglês, ou log que duplica o que o `ApiErrorResponses`/`AkariExceptionMapper` já loga
  automaticamente pra toda exceção que chega num resource (não precisa logar de novo a mesma
  exceção ali *e* no mapper — se o catch já existia no service/client por outro motivo que não
  logging, aproveita; se o único jeito de logar seria interceptar a exceção genérica antes do
  mapper, geralmente não compensa).
