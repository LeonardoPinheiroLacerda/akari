---
name: docs-interview
description: Faz curadoria dos documentos de arquitetura do akari (docs/ARQUITETURA_DO_SISTEMA.md, docs/OBJETIVO_DO_SISTEMA.md) e dos README.md de cada domain/pacote, resolvendo marcas de "Hipótese" e pontos em aberto via entrevista com o usuário. Trigger em "revisar os docs", "curadoria da documentação", "entrevista sobre os READMEs", "tirar dúvidas dos docs/READMEs", "deixar a documentação mais confiável", "vamos revisar os READMEs um por um", ou sempre que o usuário pedir pra passar pelos domínios perguntando sobre pontos em aberto. Só pergunta ao usuário o que é decisão de produto/intenção — tudo que é verificável lendo o código (grep, migrations, callers) é resolvido sozinho, sem pergunta.
---

# docs-interview

Resolve pontos em aberto na documentação do akari (`docs/*.md` e os `README.md` de
`domain/<x>/` + pacotes transversais) fazendo perguntas objetivas ao usuário — só sobre o que
*só ele* pode responder — e verificando tudo o mais sozinho, lendo o código. O objetivo final é
nunca deixar uma marca de "Hipótese" ou uma observação em aberto sem resolução: todo ponto vira
**Confirmado** (fato verificado em código), **Aceito** (decisão deliberada do mantenedor, não é
dívida), ou **Backlog real** (dívida técnica reconhecida, não urgente).

## Por que a divisão pergunta-vs-verificação importa

Perguntar algo que já dá pra responder lendo o código desperdiça o tempo do usuário e sinaliza que
você não leu o projeto direito. Mas assumir uma intenção de produto (se um comportamento é
aceitável, se algo é roadmap, se vale renomear uma classe) sem perguntar é pior — vira
documentação que parece confiável mas reflete só a sua suposição. A regra de ouro:

- **Dá pra confirmar com uma ferramenta** (grep, `Read`, `git log`, migrations, compilar) →
  verifique você mesmo, nunca pergunte. Isso cobre quase todo "não verificado"/"não inspecionado
  em profundidade"/"a confirmar" deixado por uma análise anterior.
- **Só o mantenedor sabe** (é aceitável para o volume de uso atual? é intencional ou falta
  implementar? vale renomear/reestruturar agora?) → pergunte, com opções objetivas.

## Processo

### 1. Escopo e ordem

Primeiro passam `docs/ARQUITETURA_DO_SISTEMA.md` e `docs/OBJETIVO_DO_SISTEMA.md` (visão geral do
sistema — podem exigir também corrigir o `openapi.yaml` quando a divergência for entre contrato e
implementação). Depois os `README.md` de cada domínio, **um de cada vez**, perguntando ao final de
cada um se segue para o próximo — nunca emende vários domínios na mesma leva sem o usuário pedir.

Ordem sugerida para os domínios, folhas do grafo de dependências primeiro (grafo documentado em
`ARQUITETURA_DO_SISTEMA.md`, seção "Fluxo de comunicação entre módulos" — releia de lá se o projeto
tiver crescido): `storageprovider` (+ subpacote `provider`) → `setting` → `notification` →
`mediafolder` → `videofile` → `cache` → `mediaprobe` → `anilist` → `tmdb` → `anibridge` → `anime`
→ `playback` (+ subpacote `transcode`) → `ingestion`. Por último, os pacotes transversais fora de
`domain/`: `config`, `exceptions`, `model`, `utils`, `db` (`src/main/resources/db/README.md`).

Motivo da ordem: um domínio-folha (sem dependências de saída) tem respostas mais simples e
geralmente esclarece conceitos (ex.: trade-off aceito do rclone) que reaparecem como referência
nos domínios que dependem dele — resolver a folha primeiro evita repetir a mesma pergunta depois.

### 2. Levantar os pontos em aberto do arquivo

Leia o arquivo inteiro e liste os candidatos a pergunta/verificação. Os sinais mais comuns:

- Qualquer marca **`Hipótese:`** explícita no texto — por convenção do projeto, essas marcas
  existem justamente para indicar "isto não foi confirmado, resolva depois".
  Isso vale tanto para os dois `docs/*.md` quanto pra qualquer README que tenha usado essa
  convenção.
- Frases como "não verificado", "não inspecionado em profundidade", "a confirmar", "candidato a
  dívida", "vale a pena considerar", "parece ser X mas não há evidência" — qualquer hedge que
  sinaliza que quem escreveu o README não teve certeza.
- Itens na seção "Observações técnicas e débitos identificados" que descrevem um comportamento sem
  dizer se é aceitável ou se precisa de correção.

### 3. Verificar sozinho o que for factual

Antes de perguntar qualquer coisa, tente resolver via ferramenta:

- **Existe algo no código?** `grep`/`Read` direto (ex.: "existe mecanismo de auth?" → grep por
  `@RolesAllowed`/`SecurityIdentity`; "haverá teste de X?" → checar se `src/test` existe).
  Prefira delegar essa varredura a um subagente (`Explore`/`general-purpose`) quando envolver
  vasculhar vários domínios de uma vez — isso evita poluir seu contexto com grep bruto.
- **Quem chama o quê?** Siga a cadeia real de chamadas antes de supor uma hipótese de acoplamento
  (ex.: "X só confirmado por grep de import" → abra os arquivos de verdade e veja a chamada).
- **O schema do banco garante algo?** Migrations em `src/main/resources/db/*.sql` — cascades
  (`ON DELETE CASCADE`), constraints, etc. Não aceite "a DDL provavelmente faz X" sem checar o SQL.
- **O contrato bate com a implementação?** Compare a `description` da tag/operação no
  `openapi.yaml` com o código real do service.

Atualize o arquivo com o resultado, usando a marca **Confirmado** (ou, quando for o caso de
corrigir um equívoco factual tipo um texto desatualizado, corrija o texto direto e registre o que
mudou). Cite `arquivo:linha` quando ajudar quem for reler depois.

### 4. Perguntar só o que sobrar

O que não dá pra resolver em código vira pergunta via `AskUserQuestion`. Agrupe até 4 perguntas por
chamada (o limite da ferramenta) e prefira esse formato, que rendeu bem nesta metodologia:

- Pergunta objetiva, citando o trecho/comportamento exato (não "o que você acha de X", mas "X faz
  Y, e Z — isso é problema ou é esperado?").
- 2 a 4 opções concisas, cada uma com uma frase de consequência. O par mais comum é:
  - **Aceito** / **Intencional** — decisão deliberada, não precisa virar tarefa.
  - **Backlog real** / **Lacuna a cobrir** — dívida reconhecida, mas sem pressa — ou, quando for
    algo que o usuário quer corrigir no ato (ex.: renomear uma classe), ofereça essa opção também.
- Não pergunte duas vezes pela mesma coisa: se o usuário já estabeleceu uma **política geral**
  (ex.: "os itens da seção Riscos são todos backlog real, vou corrigir aos poucos"), aplique essa
  política a todo item equivalente nos domínios seguintes sem perguntar de novo — só marque
  "**Backlog real** (confirmado pelo mantenedor)" e, se fizer sentido, referencie a decisão geral.
  Só pergunte de novo quando o item for genuinely diferente do que a política já cobriu.

### 5. Atualizar o arquivo

Troque a marca de incerteza pela resolução, direto no texto (não deixe a pergunta e a resposta só
na conversa — o ganho da entrevista é a documentação ficar confiável sozinha, sem precisar reler o
chat). Vocabulário padrão a usar inline, em negrito:

- **Confirmado** (+ evidência: arquivo:linha, nome do teste, etc.) — fato verificado em código.
- **Aceito** (confirmado pelo mantenedor) — comportamento deliberado, não é dívida.
- **Backlog real** (confirmado pelo mantenedor) — dívida reconhecida, candidata a correção futura,
  sem compromisso de prazo.
- Quando a resposta implica um **TODO item concreto que o usuário aprovou fazer agora** (ex.: "sim,
  renomeia a classe"), não documente só a intenção — execute a mudança de verdade (ver seção
  abaixo) e documente o resultado, não a promessa.

Se a resolução de um item também afeta um arquivo de um domínio **já fechado** nesta sessão (ex.:
uma decisão de roadmap que vale para rclone, ffprobe e ffmpeg ao mesmo tempo, espalhados em três
READMEs diferentes), volte e atualize os outros arquivos também — não force o usuário a repetir a
mesma resposta quando você já sabe que ela se aplica em outro lugar.

### 6. Fechar o domínio e seguir

Resuma em 1-2 frases o que foi resolvido (quantas perguntas novas, o que foi resolvido sozinho) e
pergunte explicitamente se segue para o próximo item da ordem. Não pule a confirmação — o ritmo
"um domínio por vez" é do usuário, não seu.

## Quando a entrevista vira mudança de código de verdade

Às vezes uma resposta não é só "documenta isso" — é "sim, corrige/renomeia agora". Quando o usuário
aprovar explicitamente uma ação concreta:

1. Levante **todas** as referências antes de tocar em qualquer arquivo (`grep -rn` pelo nome
   antigo, em `.java` e `.md` — não esqueça os READMEs de domínios já fechados).
2. Para rename de classe: `git mv` do arquivo, depois substitua a classe e os campos injetados
   (`@Inject`) preservando a convenção do projeto de que o nome do campo espelha o sufixo da
   classe (ver skill `code-style`).
3. Recompile (`mvn -q -o compile`) antes de dar o fluxo por encerrado — pegar um erro de referência
   esquecida agora é muito mais barato que descobrir depois.
4. Só então atualize os READMEs/docs afetados (inclusive os de domínios que só *mencionam* a classe
   renomeada, não só o domínio dono dela).
5. Nunca comitar essa mudança sem aprovação explícita e separada — mesma regra de sempre para
   commits.

## O que NÃO fazer

- Não pergunte algo que um `grep`/`Read` resolve — isso é o erro mais caro desta metodologia.
- Não invente opções de resposta fora do que a pergunta realmente admite; mantenha 2-4 opções
  claras, não uma lista longa de nuances.
- Não acumule perguntas de vários domínios numa pergunta só — cada `AskUserQuestion` deve ser sobre
  o domínio em curso.
- Não avance para o próximo domínio sem o usuário confirmar.
- Não deixe uma marca de `Hipótese` sem resolução — se você leu o arquivo e viu uma, ela entra na
  lista de pontos a cobrir nesta rodada, mesmo que pareça trivial (perguntas triviais ainda valem
  a pena quando é rápido confirmar com o mantenedor, ex.: origem de um nome de domínio).
- Não rode a bateria de evals/benchmarks do skill-creator para este fluxo — é uma metodologia de
  trabalho interno do projeto, não uma skill genérica a ser distribuída/otimizada por eval.
