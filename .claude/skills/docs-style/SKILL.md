---
name: docs-style
description: Convenção de escrita pra docs/*.md e README.md de domínio/pacote no akari — documenta só o estado atual, nunca a jornada de como chegamos lá. Trigger sempre que for editar/criar um README.md ou um dos docs/*.md (ARQUITETURA_DO_SISTEMA.md, OBJETIVO_DO_SISTEMA.md), seja numa entrevista de curadoria, seja de passagem ao implementar uma feature ou corrigir um bug. Cobre o que cortar (histórico, alternativas rejeitadas, "era planejado e já foi implementado") vs. o que manter (marcas de Aceito/Backlog real/Confirmado, as respostas do mantenedor, a razão não-óbvia do desenho atual).
---

# docs-style

Documentação do akari (`docs/*.md`, `README.md` de cada `domain/<x>/` e pacote transversal)
descreve **o estado atual do sistema**, nunca o processo de como esse estado foi alcançado. Isso
vale toda vez que um README é editado — numa entrevista de curadoria (skill `docs-interview`) ou
de passagem, ao implementar algo e atualizar a doc do domínio afetado.

## O teste

Antes de escrever uma frase sobre decisão/mudança, pergunte: **isso ajuda alguém a evoluir a
aplicação no futuro?**

- Se a frase é sobre a *jornada* (onde um tipo morava antes, que alternativa foi considerada e
  rejeitada, "à época desta análise isso não existia, foi implementado depois", "o contrato dizia
  X, estava errado, foi corrigido") — **corte**. Ninguém lendo a doc daqui a 6 meses precisa saber
  o histórico; só o que é verdade agora.
- Se a frase é uma **marca de decisão** (`Aceito`, `Backlog real`, `Confirmado`,
  `(confirmado pelo mantenedor)`) ou uma **razão não-óbvia pro desenho atual** — mesmo que essa
  razão cite uma alternativa rejeitada ("não fica em X porque isso acoplaria Y a Z") — **mantenha**.
  Essa razão evita que alguém "corrija" depois algo que não está quebrado.

## Cortar

- "`CacheEntry` morava em `model/` antes, mas violava a convenção, foi movido pra..." → só diga
  onde está agora.
- "Destino originalmente cogitado era X, mas rejeitamos porque Y" → "Fica em Z, não em X, porque Y."
  (a alternativa rejeitada pode aparecer, mas como razão do estado atual, não como narrativa de
  decisão).
- "À época desta curadoria/análise, isso ainda não existia — foi implementado depois" → descreva
  só o comportamento atual.
- "Já corrigidas no contrato:" seguido da lista do que estava errado antes → se o contrato já está
  certo, não há nada a dizer sobre o erro antigo.
- Qualquer seção/bullet que só existe pra narrar um bug/lacuna já resolvido, sem nenhum fato
  presente de valor → apague o bullet inteiro em vez de encurtar a história.

## Manter

- `Aceito`, `Backlog real`, `Confirmado` — são o resultado de uma decisão real do mantenedor,
  sinal útil pra quem for decidir se algo é problema ou não.
- `(confirmado pelo mantenedor)` — não é ruído, é proveniência da decisão. Só é dispensável quando
  o contexto já deixa claríssimo que é uma decisão (ex.: dentro de uma seção cujo parágrafo de
  abertura já diz "os itens abaixo foram confirmados com o mantenedor").
- Razão curta pro estado atual, mesmo citando o que foi descartado: "`CacheEntry` fica em
  `utils/model/`, não em `domain/cache/model/`, porque isso acoplaria o helper `CacheUtils` — hoje
  puro — a um tipo de domínio." Isso é diferente de narrar a decisão ("consideramos X, decidimos
  Y") — é só o fato atual com o porquê.
- Toda resposta do mantenedor a uma pergunta de curadoria (`AskUserQuestion`) que define
  Aceito/Backlog/Confirmado — isso é o produto da entrevista, não histórico a descartar.

## Quando estiver em dúvida

Isso é julgamento frase a frase, não um find-and-replace mecânico. Se não tiver certeza se uma
frase é "jornada" ou "razão do estado atual", pergunte ao usuário antes de fazer uma varredura
grande removendo texto — já aconteceu de interpretar rápido demais e remover confirmações que
deveriam ficar (ver `.claude/skills/docs-interview/SKILL.md` para o vocabulário de marcação usado
na curadoria).
