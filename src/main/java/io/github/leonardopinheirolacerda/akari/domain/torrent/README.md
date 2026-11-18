# torrent

## Objetivo do módulo

Animes vindos de torrent: busca releases nos provedores (nyaa hoje), baixa pelo qBittorrent para
a pasta do anime na biblioteca, ingere no catálogo e rastreia animes em lançamento para baixar os
episódios novos sozinho. Tags `TorrentSearch`, `TorrentDownload` e `TorrentTracking`, em
`/v1/torrent`.

## Responsabilidade principal

- Ler os provedores por webscrap, sempre pelo proxy HTTP do gluetun (VPN).
- Agrupar as releases em **edições** (grupo + descritores técnicos), uma release por episódio.
- Levar cada download do qBittorrent até a biblioteca: mover os arquivos, limpar o torrent,
  ingerir a pasta e notificar.
- Checar os rastreios periodicamente e baixar os episódios novos da edição rastreada.

## Funcionalidades existentes

- `GET /v1/torrent/search?provider=&q=` — busca releases agrupadas por edição. Aceita vários `q`:
  cada um é buscado (todas as páginas) e os resultados são juntados pelo info-hash. `truncated`
  indica que o provedor cortou alguma busca no limite dele.
- `POST /v1/torrent/downloads` — envia uma release ao qBittorrent para a pasta do anime.
  Idempotente pelo info-hash, que sai do próprio magnet.
- `GET /v1/torrent/downloads` — página dos downloads, do mais recente para o mais antigo, com a
  miniatura e o título do episódio e, com `userId`, o progresso do perfil nele.
- `GET|POST /v1/torrent/tracked`, `DELETE /v1/torrent/tracked/{trackedId}` — rastreios.
- `PUT /v1/torrent/tracked/{trackedId}/queries` — troca as queries de um rastreio.
- `GET /v1/torrent/tracked/{trackedId}/releases` — refaz a busca e devolve só a edição rastreada.
- `TorrentDownloadScheduler` (10 s) — sincroniza os downloads com o qBittorrent.
- `TorrentTrackingScheduler` (15 min) — checa os rastreios.

## Dependências internas e externas

- `anime` — vincula a pasta nova ao anime, resolve título, banner e capa, lê o pareamento TMDB
  do episódio.
- `anilist` — título romaji (nome da pasta) e capa do rastreio.
- `mediafolder` — cria a pasta do anime na biblioteca.
- `storageprovider` — provider da biblioteca e `importFiles` para mover os arquivos.
- `ingestion` — ingere a pasta depois do download; `EpisodeNumberExtractor` dá o número do
  episódio das releases e dos arquivos.
- `videofile` — acha o arquivo do episódio de cada download.
- `user` — progresso do perfil nos episódios baixados.
- `setting` — preferência `torrent.library-provider-id`.
- `notification` — avisa download concluído e falha de ingestão.
- `playback` — `PlaybackWorkspaceService.delete` apaga a pasta de trabalho do torrent.
- Externo: provedores de torrent (Jsoup, `HttpClient` pelo proxy `akari.torrent.proxy.*`) e
  qBittorrent (REST client `quarkus.rest-client.qbittorrent`, login
  `akari.torrent.qbittorrent.*`).

## Módulos relacionados

Nenhum domínio depende de `torrent`; ele é consumido pela camada HTTP. O portal usa a busca e o
rastreio; o app de TV usa `GET /v1/torrent/downloads` na fileira Novidades.

## Pontos de entrada

- `resource/TorrentSearchResource`, `TorrentDownloadResource`, `TorrentTrackingResource` — só
  delegam.
- `service/TorrentSearchService` — busca, agrupamento e a edição rastreada.
- `service/TorrentDownloadService` — download, sincronização com o qBittorrent e listagem.
- `service/TorrentTrackingService` — rastreios e a checagem periódica.
- `provider/TorrentProviderClientFactory.getClient(TorrentProviderType)` — client do provedor.

## Fluxos importantes

**Busca:** `TorrentProviderClient.search` lê a página 1 (para saber o total) e as demais em
paralelo, no máximo 4 ao mesmo tempo → `ReleaseTitleExtractor` tira grupo, versão, chave e rótulo
da edição; `EpisodeNumberExtractor`, o episódio → agrupa por chave → em cada edição fica uma
release por episódio (maior versão, depois mais seeders); as numeradas vêm em ordem de episódio e
as sem número (pacotes) no fim → cada release leva o estado de download salvo.

**Edição:** a chave é o grupo mais os descritores técnicos do título (temporada, resolução, fonte,
mídia, áudio, codec, profundidade de cor). Nome do anime, número do episódio e texto livre ficam
de fora, então um grupo que muda o nome no meio da temporada continua na mesma edição.

**Download:** info-hash do magnet → já baixando ou pronto devolve como está → biblioteca
(preferência, só `LOCAL`) → pasta do anime: a vinculada (se for de outro provider, `409`) ou uma
nova `<título romaji>` vinculada na hora → qBittorrent baixa em
`<TORRENT_DOWNLOAD_DIR>/<info-hash>` → release `DOWNLOADING`.

**Sincronização (10 s):** para cada `DOWNLOADING`: fora do qBittorrent → `NEW`; baixando →
progresso; completo → `importFiles` move tudo para a pasta do anime → remove o torrent e a pasta
de trabalho → ingere a pasta → `READY` → notificação. Cada release grava numa transação própria,
e falha numa não para as outras.

**Rastreio:** ao criar, busca completa para conferir a edição e gravar como `latestEpisode` o
maior episódio que ela já tem — só os seguintes são baixados sozinhos. Na checagem (15 min), lê a
página 1 de cada query; se ela não alcança `latestEpisode`, usa a busca completa. Episódios acima
de `latestEpisode` vão para download, em ordem. Falha fica em `lastError`; `429` encerra o ciclo.

## Arquivos críticos

- `service/TorrentDownloadService.java` — download, sessão do qBittorrent e fim do download.
- `service/TorrentTrackingService.java` — regra de episódio novo.
- `extractor/ReleaseTitleExtractor.java` — regex da edição; mudar aqui muda o agrupamento e os
  rastreios existentes (a chave gravada deixa de bater).
- `provider/client/ScrapingTorrentProviderClient.java` — paginação, proxy e erros comuns a todos
  os provedores.
- `provider/client/impl/NyaaTorrentProviderClient.java` — particularidades do nyaa.

## Observações técnicas e débitos identificados

- **Provedor novo:** uma subclasse de `ScrapingTorrentProviderClient` (URL da página, tamanho de
  página, limite de resultados, total e leitura do HTML), um valor em `TorrentProviderType` e um
  `case` na fábrica. Cada provedor tem uma instância só, e `search` é `synchronized` para o limite
  de 4 requests valer por provedor.
- **Sem fallback de rede:** sem o gluetun no ar, a busca falha (`502`); o scrape nunca sai pela
  conexão direta.
- **Só biblioteca `LOCAL`:** `importFiles` de provider remoto lança
  `UnsupportedOperationException`.
- **Nyaa:** busca na categoria `1_2`, ordem por id, 75 por página e no máximo 1000 resultados;
  `429` vira `RateLimitedException` com o `Retry-After`.
- **Um rastreio por anime** (`anilist_id` único), numa edição só.
- **Aceito**: `TorrentTrackingService` chama `TorrentSearchService` e `TorrentDownloadService`,
  services do mesmo domínio — o rastreio é a orquestração dos dois.
- **qBittorrent:** a sessão (cookie) é reaproveitada e as chamadas são serializadas; `403` refaz o
  login uma vez.
- **Batch:** um pacote só vai para a biblioteca com o torrent inteiro pronto, porque as peças
  cruzam a fronteira entre arquivos.
