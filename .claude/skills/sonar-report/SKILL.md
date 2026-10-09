---
name: sonar-report
description: Lê o report do SonarCloud do projeto akari, separando Overall Code de New Code — quality gate, bugs, vulnerabilidades, code smells, security hotspots, cobertura e duplicação. Trigger em "report do sonar", "status do sonar", "veja o sonar", "qualidade do código", "quality gate", "new code", "overall code".
---

# sonar-report

Lê o estado atual da análise do SonarCloud via API pública, sem precisar abrir a UI.

## Projeto

- Organization: `leonardopinheirolacerda`
- Project key: `akari`
- O projeto é público — os endpoints de leitura abaixo não exigem token. Se um dia o
  projeto virar privado, todos passam a exigir `-u "$SONAR_TOKEN:"` (Basic Auth, senha
  vazia).
- Base: `https://sonarcloud.io/api`

## Quality Gate

```bash
curl -s "https://sonarcloud.io/api/qualitygates/project_status?projectKey=akari"
```

`projectStatus.status` é `OK`, `ERROR`, `WARN` ou `NONE` (gate ainda não avaliado ou
sem condições configuradas). Cada item de `conditions[]` tem um `metricKey`: prefixo
`new_` é condição de **New Code**, sem prefixo é **Overall Code** — é assim que se
distingue uma da outra nessa resposta.

## Overall Code — métricas agregadas

```bash
curl -s "https://sonarcloud.io/api/measures/component?component=akari&metricKeys=bugs,vulnerabilities,code_smells,security_hotspots,coverage,duplicated_lines_density,ncloc,reliability_rating,security_rating,sqale_rating"
```

## New Code — métricas agregadas

```bash
curl -s "https://sonarcloud.io/api/measures/component?component=akari&metricKeys=new_bugs,new_vulnerabilities,new_code_smells,new_security_hotspots,new_coverage,new_duplicated_lines_density,new_lines"
```

**Atenção:** em projetos com poucas análises, essas métricas `new_*` podem voltar
com `measures: []` (vazio) mesmo havendo issues reais no período — o Compute Engine
do SonarCloud não recalcula o leak period de imediato. Quando isso acontecer, não
reporte "sem dados no new code" — use a lista de issues/hotspots filtrada abaixo, que
é a fonte confiável.

## Lista de issues (bugs, vulnerabilidades, code smells)

Overall (tudo que está aberto):

```bash
curl -s "https://sonarcloud.io/api/issues/search?componentKeys=akari&resolved=false&ps=100&facets=severities,types,rules"
```

Só o que caiu no período de New Code:

```bash
curl -s "https://sonarcloud.io/api/issues/search?componentKeys=akari&resolved=false&inNewCodePeriod=true&ps=100&facets=severities,types,rules"
```

`facets` já devolve a contagem por severidade/tipo/regra — usa isso pra montar o
resumo em vez de paginar e contar manualmente, exceto quando for listar as issues
individuais.

## Security Hotspots

```bash
curl -s "https://sonarcloud.io/api/hotspots/search?projectKey=akari&ps=100"
curl -s "https://sonarcloud.io/api/hotspots/search?projectKey=akari&ps=100&inNewCodePeriod=true"
```

## Nome legível de uma regra

A busca de issues só devolve a key da regra (ex: `java:S6813`), não o nome nem a
descrição. Pra exibir algo legível:

```bash
curl -s "https://sonarcloud.io/api/rules/show?key=java:S6813&organization=leonardopinheirolacerda"
```

(o parâmetro `organization` é obrigatório nesse endpoint — sem ele a API devolve erro).

## Como apresentar o resultado

- Sempre duas seções separadas, **Overall Code** e **New Code** — nunca misturar os
  números das duas.
- Por seção: status do Quality Gate (se aplicável), bugs, vulnerabilidades, code
  smells (por severidade), security hotspots, coverage, duplicação.
- Agrupe issues repetidas pela mesma regra (ex: "31x S6813 — injeção por campo") em
  vez de listar uma por uma; só detalhe individualmente as de severidade
  CRITICAL/BLOCKER ou quando o usuário pedir a lista completa.
- Resposta em português.
