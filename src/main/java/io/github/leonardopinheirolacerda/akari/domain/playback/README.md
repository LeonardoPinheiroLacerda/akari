# Domínio `playback`

## Objetivo do módulo

Orquestrar sessões de reprodução de mídia via transcode HLS sob demanda: iniciar o transcode de um arquivo de vídeo já disponível em disco (provider `LOCAL`, lido direto; ou provider remoto, já baixado no cache), permitir reposicionamento (`seek`) e troca de faixas de áudio/legenda, servir a playlist `m3u8` gerada e seus arquivos, e manter viva a sessão por heartbeat do cliente.

## Responsabilidade principal

É o maior domínio do projeto (~2957 linhas, 35 arquivos) porque concentra duas responsabilidades de peso:

- **Ciclo de vida da sessão**: criação, reposicionamento, troca de faixas, expiração por inatividade, limpeza de processo e de arquivos em disco.
- **Montagem do comando `ffmpeg`**: decidir *o que* transcodificar (perfil → estratégia → spec estruturada) e traduzir essa spec em argumentos de linha de comando reais.

A sessão (`PlaybackSession`) é 100% efêmera: não existe um "status encerrado" persistido — o estado é inferido pelos campos (pid, `processStartedAt`, `lastHeartbeatAt`) e a linha é apagada quando a sessão termina.

## Funcionalidades existentes

- Disponibilidade do arquivo (`GET /v1/playback/files/{fileId}/availability`): sem efeito colateral (não enfileira nada), diz se o arquivo já pode tocar (`READY`: provider `LOCAL`, ou remoto com cache `COMPLETED`), precisa ser baixado (`NEEDS_DOWNLOAD`: remoto sem linha de cache, ou linha `FAILED`) ou já está baixando (`DOWNLOADING`: remoto com linha `QUEUED`/`DOWNLOADING`) — com o `cacheItemId` da linha, quando existir, pro cliente correlacionar com o SSE de `/v1/cache/events`.
- Iniciar sessão de transcode (`POST /v1/playback/sessions`): resolve o arquivo em disco, valida índices de faixa, escolhe a estratégia de transcode pelo profile (`TranscodeProfile`: `WEB`/`TV`/`MOBILE`/`GOOGLE_TV_STREAMER`, enum fixo do contrato — não configurável via `Setting`), sobe o `ffmpeg` e persiste a sessão.
- Seek (`POST .../seek`): se o instante pedido já está dentro da janela já transcodificada (lida da playlist, com tolerância de 0.5s), não reinicia o processo; fora da janela, reinicia o `ffmpeg` do novo offset reaproveitando as faixas ativas. Com o vídeo copiado (remux), o `startOffsetSeconds` devolvido é o keyframe onde o transcode de fato recomeça, não o instante pedido — ver fluxo de seek.
- Troca de faixas (`POST .../tracks`): sempre reinicia o transcode — trocar `-map`/filtro de legenda exige novo processo `ffmpeg`, não há posição que evite o reinício.
- Servir playlist (`GET .../playlist.m3u8`) e arquivos HLS (`GET .../{file}`: segmentos, variant playlists, legendas WebVTT, init segment), com validação de nome por regex (proteção contra path traversal). Responde `404` enquanto o transcode ainda está gerando a playlist — inclusive um master playlist já escrito mas ainda sem nenhuma variante (`#EXT-X-STREAM-INF` com URI), que o player não consegue usar; se o `ffmpeg` já saiu com erro e ela nunca vai aparecer, responde `TRANSCODE_FAILED` (500) em vez de ficar presa no `404` indefinidamente. Um `ffmpeg` que terminou com código `0` concluiu o transcode: a sessão continua servindo playlists e arquivos, aceitando heartbeat, seek e troca de faixas normalmente.
- Heartbeat (`POST .../heartbeat`): renova o TTL da sessão e devolve a validade restante.
- Varredura periódica (a cada 5s) que encerra e remove sessões sem heartbeat dentro do TTL configurado.
- Suporte a HLS de rendition única ou ABR (múltiplas resoluções, master + variant playlists, cada rung preservando a proporção real do vídeo), legenda soft (WebVTT, para SRT), burn-in de texto (demais codecs de texto, ex. ASS, via libass) ou burn-in de imagem (PGS/VobSub/DVB, via overlay do próprio stream de legenda decodificado sobre o vídeo), correção automática de SAR (vídeo anamórfico sempre sai com pixel quadrado, na proporção de exibição real — não depende do player respeitar a SAR), e aceleração de hardware de vídeo (VideoToolbox/NVENC/QSV/VAAPI) conforme preferência do admin.

## Dependências internas e externas

Dependências de saída (único sentido — ver "Observações técnicas"):

- `domain/videofile` (`VideoFileService`, `VideoFile`) — resolve o arquivo sendo reproduzido.
- `domain/mediaprobe` (`MediaProbeService`) — metadados técnicos (codecs, faixas, frame rate) usados tanto para validar índices de faixa quanto para montar o comando `ffmpeg`.
- `domain/storageprovider` (`StorageProviderClientFactory`) — resolve o path local do arquivo (via `VideoFileLocationUtils`, em `utils/`, compartilhado com `mediaprobe`).
- `domain/setting` (`SettingService`) — preferências `playback.work.base-dir`, `playback.session.heartbeat-ttl`, `playback.transcode.hardware-acceleration`.
- `domain/cache` (`CacheService.findCacheItemByFileId`) — status da linha de cache de um arquivo remoto, usado por `getFileAvailability`. Também indiretamente via `VideoFileLocationUtils`, pra resolver o path em disco de um arquivo remoto já baixado.

Dependência externa: binário `ffmpeg` resolvido via `PATH` (config `akari.playback.ffmpeg.binary`, default `ffmpeg`), chamado via `ProcessBuilder`. Nenhuma validação de disponibilidade no startup — só falha na primeira sessão iniciada. **Aceito por ora, mas roadmap já desenhado** (confirmado pelo mantenedor): vai existir um readiness check central em `config/` (ex.: `/q/health/ready`) validando dependências de infra — este binário, `ffprobe` e o processo `rclone rcd` — ver `config/README.md`, `mediaprobe/README.md` e `storageprovider/provider/README.md`.

## Módulos relacionados

Nenhum outro domínio depende de `playback` — confirmado por busca textual (`grep -rl "akari.domain.playback"` fora do próprio pacote não retorna nada). É um domínio "folha" na árvore de dependências, consumido apenas pela camada HTTP (via `PlaybackResource`/contrato OpenAPI).

## Pontos de entrada

- `resource/PlaybackResource` — implementa `PlaybackApi` (gerado do `openapi.yaml`, tag `Playback`), delega 100% ao `PlaybackService`. Também usa um `@ServerResponseFilter` para adicionar o header `X-Playback-Heartbeat-Ttl-Seconds`, o `Content-Type` correto por extensão e suporte a `Range`/`206 Partial Content` (necessário para HLS nativo do Safari) — coisas que a interface gerada não expressa.
- `scheduler/PlaybackScheduler` — dispara `PlaybackService.sweepExpiredSessions()` a cada 5s.

## Fluxos importantes

**Disponibilidade**: `PlaybackService.getFileAvailability` resolve o `VideoFile` → se o provider for `LOCAL`, `READY` direto (sem checar `cacheItemId`, sempre `null`); se remoto, busca a linha de cache do arquivo via `CacheService.findCacheItemByFileId` — sem linha ou linha `FAILED` vira `NEEDS_DOWNLOAD`, `QUEUED`/`DOWNLOADING` vira `DOWNLOADING`, `COMPLETED` vira `READY`, sempre com o `cacheItemId` da linha quando ela existe. Mesma condição de `COMPLETED` que `VideoFileLocationUtils.resolvePath` usa internamente pra resolver o arquivo no disco — não há etapa entre os dois.

**Início de sessão**: `PlaybackResource` → `PlaybackService.startPlaybackSession` → resolve `VideoFile` e path em disco → probe de mídia → valida índices de faixa → `TranscodeProfileSelector.select(profile, probe)` escolhe a `HlsTranscodeStrategy` vencedora → `strategy.buildSpec(probe)` gera a `HlsTranscodeSpec` → cria o diretório de trabalho (`<work-base-dir>/<sessionId>`) → `FfmpegClient.start` monta o comando e sobe o processo, registrando-o no `TranscodeProcessRegistry` → espera até 600ms (checando a cada 100ms) pra confirmar que o processo não falhou de cara — se sair com código diferente de `0` (ou por sinal), apaga o `workDir` recém-criado e responde `TRANSCODE_FAILED` sem persistir a sessão; sair com `0` dentro da janela é conclusão normal (com o vídeo copiado, o remux de um trecho curto, ex. seek perto do fim, termina em menos de 1s) → persiste `PlaybackSession` com pid, `workDir`, `processStartedAt`. O mesmo reinício (seek fora da janela, troca de faixas) passa pela mesma checagem de 600ms.

**Seek**: lê a playlist (`stream_0.m3u8` se ABR, senão `index.m3u8`) e soma as durações `#EXTINF` (`HlsPlaylistParser`) para saber quanto já foi transcodificado; se o instante pedido cai dentro da janela `[startOffset - 0.5s, startOffset + transcodado + 0.5s]`, devolve a sessão sem reiniciar nada; senão, encerra o processo atual, limpa o workspace e sobe um novo `ffmpeg` a partir do novo offset. Recodificando o vídeo, o `-ss` corta exatamente no instante pedido. Copiando (`FfmpegClient.copiesVideo`), o ffmpeg só consegue começar no keyframe onde a busca do demuxer cai — que segue o índice do container (no Matroska, os `Cues`, que podem não listar keyframes de corte de cena), então não é necessariamente o último keyframe antes do instante. O `restart` pergunta esse keyframe ao `MediaProbeService.findSeekKeyframe` (o ffprobe faz a mesma busca do ffmpeg), devolve ele como `startOffsetSeconds` e passa `-ss` = keyframe + 0.15s: com B-frames o ffmpeg busca `3/23`s antes do `-ss`, e um `-ss` exatamente no keyframe cairia no keyframe anterior do índice. Sem keyframe encontrado, usa o instante pedido.

**Escrita dos arquivos HLS**: as duas estratégias ligam `-hls_flags temp_file` — o ffmpeg escreve master, variant playlists, playlist WebVTT e segmentos num `.tmp` e renomeia ao terminar, então o backend nunca serve um arquivo truncado no meio de uma reescrita (os nomes `.tmp` não passam na regex de arquivos servidos). Com o vídeo copiado, o `FfmpegClient` também passa `-b:v` com o bitrate do container (do probe): o muxer HLS calcula o `BANDWIDTH` do master a partir do `bit_rate` dos streams, e sem ele (comum em Matroska) escreve o master sem o `#EXT-X-STREAM-INF` e só o completa no fim do remux.

**Encerramento (por reinício ou expiração)**: `PlaybackService.terminateAndCleanup` primeiro marca no `TranscodeProcessRegistry` que o encerramento foi pedido pelo backend (pra o processo não ser logado como falha quando sair) → `ProcessTerminationService.terminate(pid, processStartedAt)` confirma que o pid ainda pertence ao processo esperado (compara o instante real de início do processo vivo com `processStartedAt`, tolerância de 10s, para não matar um processo que o SO reaproveitou) — manda SIGTERM, espera até 5s, força SIGKILL se necessário → `PlaybackWorkspaceService.delete(workDir)` apaga recursivamente o diretório da sessão (best-effort, idempotente) → o registry esquece a sessão.

**Varredura de expiração**: `PlaybackScheduler` (a cada 5s) → `PlaybackService.sweepExpiredSessions()` lista todas as sessões e, para cada uma com `isExpired(now, ttl)` verdadeiro (sem heartbeat, ou sem criação, há mais que o TTL), encerra o processo, limpa o workspace e apaga a linha — falha em uma sessão não impede a varredura das demais.

## Arquivos críticos

- `service/PlaybackService.java` — orquestração central; todo o ciclo de vida da sessão passa por aqui.
- `client/FfmpegClient.java` — monta o comando `ffmpeg` completo (rendition única e ABR); é o arquivo mais complexo do domínio, descrito em detalhe no README de `transcode/`.
- `model/PlaybackSession.java` — entidade Panache que modela o estado efêmero da sessão.
- `service/ProcessTerminationService.java` — gerência de processo genérica, com proteção contra reaproveitamento de pid pelo SO.
- `registry/TranscodeProcessRegistry.java` — rastreia em memória, por sessão, o `Process` do `ffmpeg` em si: se ainda está vivo, como terminou (código `0` = concluído; outro código ou sinal = falha — decidido pelo código de saída guardado no próprio `Process`), se o encerramento foi pedido pelo backend (seek, troca de faixa, expiração — pra não logar como falha) e as últimas 50 linhas do stderr, capturadas em tempo real (o `workDir` é apagado na limpeza, não dá pra ler depois). É o que permite a playlist e o `POST /sessions`/`seek`/`tracks` distinguirem "ainda gerando" de "nunca vai gerar".
- `service/PlaybackWorkspaceService.java` — limpeza best-effort do diretório de trabalho.
- `parser/HlsPlaylistParser.java` — lê quanto já foi transcodificado, decisivo para o `seek` não reiniciar sem necessidade, e se o master playlist já lista alguma variante.
- `transcode/` — ver README dedicado; contém a seleção de perfil/estratégia e os specs estruturados consumidos pelo `FfmpegClient`.

## Observações técnicas e débitos identificados

- **Processo órfão em caso de crash do app**: o pid do `ffmpeg` só é conhecido e encerrado através da linha `PlaybackSession` (no reinício ou na varredura). Se o processo da aplicação cair, o `ffmpeg` continua rodando indefinidamente — não há nenhuma rotina de "adoção"/reconciliação na inicialização que escaneie processos órfãos a partir das sessões persistidas (ou vice-versa). **Backlog real** (confirmado pelo mantenedor).
- **Workspace órfão na mesma situação**: o diretório de trabalho só é limpo por `terminateAndCleanup`, chamado em reinício ou expiração. Um crash no meio de uma sessão ativa deixa o diretório em disco permanentemente, sem rotina de limpeza na inicialização. **Backlog real** (confirmado pelo mantenedor).
- **Confirmação de posse do pid pro encerramento é best-effort**: `ProcessTerminationService` usa tolerância de 10s entre `processStartedAt` registrado (persistido na sessão) e o início real do processo vivo — mitigação razoável contra reaproveitamento de pid pelo SO, mas não é garantia absoluta. É deliberadamente independente do `TranscodeProcessRegistry` (que guarda o `Process` em memória, mas só pra liveness/stderr, não pra decidir se é seguro matar): o registry não sobrevive a um restart da aplicação, enquanto o encerramento por pid+timestamp persistido continua funcionando mesmo depois de um restart — por isso os dois mecanismos não se fundem.
- **Detecção de falha do ffmpeg não sobrevive a restart**: como o `TranscodeProcessRegistry` é só em memória, uma sessão iniciada antes de um restart do akari perde esse rastreio — a playlist volta a responder só `404` "ainda não gerada" (nunca `TRANSCODE_FAILED`) até a sessão expirar por heartbeat. Mesma limitação que já se aplica a qualquer estado em memória deste domínio (sessão 100% efêmera, ver acima).
- **Concorrência entre sessões**: nada no código sincroniza sessões diferentes do mesmo `fileId` — duas sessões simultâneas sobre o mesmo arquivo rodam dois processos `ffmpeg` independentes em diretórios separados, sem controle de recurso (CPU/E/S) entre elas. **Backlog real** (confirmado pelo mantenedor).
- **Lock por sessão**: operações sobre a mesma sessão (`seek`, troca de faixas, heartbeat) não têm nenhum lock explícito além da transação do banco; duas chamadas concorrentes de `seek`/`tracks` na mesma sessão podem gerar condição de corrida entre a leitura/gravação de `ffmpegPid`/`workDir` e a criação do novo processo (não tratado no código). **Backlog real** (confirmado pelo mantenedor).
- Essas lacunas (processo e workspace órfãos, ausência de lock por sessão) são reais — nenhuma rotina de reconciliação na inicialização ou lock explícito foi encontrada no código lido. Todas confirmadas como backlog real pelo mantenedor, mesma categoria do risco geral já documentado em `ARQUITETURA_DO_SISTEMA.md`.
