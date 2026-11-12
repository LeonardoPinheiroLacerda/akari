## Objetivo do módulo

Listas por critério exibidas no front. Cada lista guarda um filtro fixo e uma ordenação, não
títulos fixos: os itens são recalculados a cada consulta. Tag `SmartList` do contrato, API
`/v1/smart-lists`.

## Responsabilidade principal

Cadastrar as listas (nome, posição, filtro, ordenação) e calcular os itens de cada uma: as raízes
de franquia que passam no filtro, já paginadas e no formato de card do catálogo.

## Funcionalidades existentes

- `GET /v1/smart-lists` — lista as listas por `position` crescente, desempate por `id`.
  `position` é um número livre: não é renumerado ao mexer em outra lista, e posições repetidas
  são permitidas.
- `POST`/`GET`/`PUT`/`DELETE /v1/smart-lists/{listId}` — cadastro das listas. `criteria` é
  obrigatório no payload; `{}` não filtra nada. `sortDirection` tem default `DESC`.
- `GET /v1/smart-lists/{listId}/items` — itens paginados, como `AnimeBindingSummaryPage`.

## Regras dos itens

As regras abaixo foram confirmadas com o mantenedor.

- Cada item é a raiz de uma franquia (`FranchiseResolver`).
- Critérios de franquia (`includeGenres`, `excludeGenres`, `includeTags`, `excludeTags`,
  `studios`, `statuses`, `adult`): a raiz entra se algum membro, incluindo ela, casar todos ao
  mesmo tempo — no **mesmo membro**. Ex.: "Comedy + tag Baseball" não traz uma franquia em que um
  membro tem `Comedy` e outro tem `Baseball`.
- Critérios de raiz (`formats`, `yearFrom`, `yearTo`, `minScore`, `minMeanScore`,
  `minPopularity`): valem só pra raiz. O ano é o `seasonYear`.
- `include*` exige todos os valores; `exclude*` recusa qualquer um; `studios`, `statuses` e
  `formats` aceitam qualquer um da lista.
- `adult` segue a regra de franquia: uma franquia com membros adultos e não adultos aparece tanto
  em `adult: true` quanto em `adult: false`. Anime sem `isAdult` conta como não adulto.
- Animes fora de qualquer franquia (ocultos, direto ou por herança) são ignorados: nunca casam
  nem viram item.
- Ordenação pelo valor da raiz, valores ausentes no fim (nas duas direções), desempate pelo
  `anilistId`. `TITLE` usa o título exibido (override, se houver).

## Dependências internas e externas

- `anime` — `Anime` (dados da coleção), `FranchiseResolver`/`FranchiseResolution` (raiz de cada
  anime) e `AnimeOverrideService` (`toBindingSummary` pro card, `resolveTitle` pra ordenação por
  título).
- `exceptions.ResourceNotFoundException` — lista inexistente.

## Módulos relacionados

Nenhum domínio depende de `smartlist`; é consumido só pela camada HTTP.

## Pontos de entrada

- `SmartListResource` (implementa `SmartListApi` gerado) — só delega pro `SmartListService`.

## Fluxos importantes

- `listSmartListItems`: `FranchiseResolver.resolve()` + `Anime.listAll()` → descarta os animes
  sem raiz (ocultos) → membros que casam os critérios de franquia viram o conjunto das suas
  raízes → filtra as raízes pelos critérios de raiz → ordena → pagina em memória (`subList`) →
  `AnimeOverrideService.toBindingSummary`.
- `createSmartList`/`updateSmartList`: `SmartListMapper.updateFromRequest` copia o payload pro
  model; `criteria` é gravado como `JSONB` (`SmartListCriteria`, via `@JdbcTypeCode`).

## Arquivos críticos

- `service/SmartListService.java` — CRUD e todo o cálculo dos itens (`matchesFranchise`,
  `matchesRoot`, `comparatorOf`).
- `model/SmartListCriteria.java` — forma do JSON gravado em `smart_list.criteria`; mudar um campo
  aqui afeta as listas já gravadas.

## Observações técnicas e débitos identificados

- O cálculo dos itens lê a coleção inteira a cada consulta, em memória, mesmo custo da resolução
  de franquias (ver `domain/anime/README.md`). Com ~900 animes o custo é baixo; cresce
  linearmente com a coleção.
- Gêneros, tags, estúdios, status e formatos são comparados por igualdade exata, com os nomes da
  AniList (`Comedy`, não `comedy`) — um valor digitado diferente não casa nada, sem erro.
- `page` negativo responde `500` (o contrato não limita `page`/`size`, igual às demais
  listagens); `size=0` devolve página vazia.
- `sortDirection: null` explícito no payload responde `500` (coluna `NOT NULL`); sem o campo,
  vale o default `DESC`.
