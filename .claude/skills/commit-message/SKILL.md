---
name: commit-message
description: Compose a Conventional Commits message in Portuguese for the staged/unstaged changes. Trigger on "commit", "mensagem de commit", "gerar commit", "faz o commit", or when the user asks to commit changes. Produces a short subject + detailed bullet list body. Never adds authorship attributions.
---

# commit-message

Generates a Conventional Commits message (in Portuguese) for the current diff. Subject on one line, blank line, then bullet points describing what changed and why.

## Absolute rules

- **Never** add `Co-Authored-By:`, `Signed-off-by:`, `Generated with...`, `🤖`, "Claude", "AI", "assistant" or any other authorship/tool attribution. The commit is the user's, period.
- **Never** use `git commit -m "..."` with the message inline for multi-line bodies — use a HEREDOC. Newlines and quotes get mangled otherwise.
- **Never** use `--no-verify` or bypass hooks unless the user explicitly asks.
- **Never** run `git add -A` or `git add .` — stage explicit paths. If files are already staged, honor the current staging.

## Before writing the message

Run in parallel:

```bash
git status
git diff --staged
git diff             # only if nothing is staged, to see what would be added
git log -n 5 --oneline
```

Use `git log` to match existing tone and scope naming in the repo. If the repo has zero commits, follow the defaults below.

If there are no staged changes and no unstaged changes, do **not** create an empty commit — tell the user and stop.

If there are only unstaged changes, do **not** stage them automatically. Ask which files to include, or ask the user to `git add` first.

## Format

```
<tipo>(<escopo>): <descrição curta no imperativo, minúscula, sem ponto final>

- <tópico 1: o que mudou e por quê>
- <tópico 2>
- <tópico 3>
```

### Subject

- **Tipo** (obrigatório) — um dos permitidos abaixo.
- **Escopo** (opcional, entre parênteses) — domínio ou parte afetada (`catalog`, `playback`, `exceptions`, `contract`, `skills`, `ci`). Omita quando a mudança for global.
- **Descrição** — verbo no imperativo, minúscula, em português, sem ponto final. Limite ≤ 72 caracteres incluindo tipo/escopo.

Verbos preferidos: `adiciona`, `remove`, `corrige`, `renomeia`, `move`, `extrai`, `atualiza`, `refatora`, `documenta`, `simplifica`, `padroniza`, `desabilita`.

### Body

- Linha em branco separando do subject (obrigatório).
- Bullets com `- ` no início.
- Cada bullet: uma alteração relevante. Descreva **o que** mudou e, se não for óbvio, **por quê**.
- Português, imperativo ou descrição direta. Sem ponto final nos bullets.
- Se um bullet precisar de mais contexto, quebre em sub-bullets com 2 espaços de indentação.
- Máximo ~5 bullets no nível principal. Se passar disso, considere se o commit não deveria ser dividido.

### Footer (opcional)

- `BREAKING CHANGE: <descrição>` — só quando há mudança incompatível.
- `Refs: #123` — só quando o usuário fornecer o número da issue.
- Nunca mais nada. Sem attribution.

## Tipos permitidos

| Tipo | Uso |
|---|---|
| `feat` | nova funcionalidade visível ao usuário/API |
| `fix` | correção de bug |
| `refactor` | mudança de código sem alterar comportamento externo |
| `perf` | ganho de performance |
| `test` | adição/ajuste de testes |
| `docs` | apenas documentação (README, Javadoc, skills, CLAUDE.md) |
| `style` | formatação, whitespace, ponto-e-vírgula — sem lógica |
| `build` | build system, Maven, dependências, plugins |
| `ci` | pipelines, GitHub Actions, hooks |
| `chore` | tarefas de manutenção sem impacto em código de produção |
| `revert` | desfaz um commit anterior |

Se a mudança combina tipos, escolha o que descreve o **efeito principal** e explique o resto nos bullets.

## Como escolher o escopo

O akari é um único módulo, com pacotes por tipo de componente (`resources`, `services`,
`models`, `mappers`, `clients`, `exceptions`, `config`). Uma funcionalidade costuma tocar vários
desses pacotes ao mesmo tempo, então o escopo é o **domínio**, não o pacote.

- Prefira o domínio afetado: `catalog`, `playback`, `cache`, `ingestion`, `settings`, `tmdb`, ...
  — mesmo que a mudança passe pelo resource, service, model e mapper dele.
- Use o nome do pacote só para mudança transversal, que não pertence a um domínio:
  `exceptions`, `config`.
- Use `contract` para mudanças no contrato OpenAPI (`src/main/resources/META-INF/openapi.yaml`).
- Mudanças no `pom.xml` usam o tipo `build`, sem escopo (ou com o nome da dependência/plugin, se ajudar).
- Use `skills` para mudanças em `.claude/skills/*`.
- Omita o escopo quando a mudança atravessa vários domínios.

## Exemplos

**Mudança pequena (feat):**

```
feat(catalog): adiciona listagem de pastas por provider

- implementa listFolders no MediaFoldersResource com filtro por storageProviderId
- adiciona a consulta findByProvider no model MediaFolder, com paginação 0-based
```

**Mudança no contrato:**

```
feat(contract): devolve o provider no corpo do PUT de config

- troca o 204 sem corpo por 200 com StorageProviderResponse
- alinha com os demais PUT do contrato, que devolvem o recurso atualizado
```

**Fix pontual:**

```
fix(exceptions): corrige o campo das violações de validação

- devolve o caminho do campo no body (config.password) em vez de argumento.campo
- usa o nome do parâmetro quando a violação é de query ou path
```

**Build:**

```
build: aponta o openapi-generator para o contrato em META-INF

- move o contrato unificado para src/main/resources/META-INF/openapi.yaml
- a mesma fonte alimenta a geração de código e o /q/openapi
```

## Como criar o commit

Sempre via HEREDOC para preservar quebras e crases:

```bash
git commit -m "$(cat <<'EOF'
<tipo>(<escopo>): <descrição>

- <bullet 1>
- <bullet 2>
EOF
)"
```

Depois, rode `git status` para confirmar o commit.

Se o pre-commit hook falhar, **não** use `--amend` — o commit anterior não existe ainda. Corrija o problema, re-stage os arquivos alterados e crie um novo commit.

## Reject

- Mensagens em inglês (a menos que o usuário explicitamente peça).
- Subject terminando em ponto final.
- Verbos no gerúndio (`adicionando`, `corrigindo`) ou passado (`adicionou`) — sempre imperativo.
- Body sem linha em branco após o subject.
- Bullets com formatação irregular (`*`, `•`, sem hífen).
- Menção a Claude, IA, assistente, autoria automatizada ou ferramenta de geração — em qualquer campo, incluindo footer.
- `git add -A` / `git add .` — stage explícito.
- `--no-verify`, `--no-gpg-sign` — só se o usuário pedir.
- Commits que misturam refactor + feature + fix num body genérico. Sugira dividir.
