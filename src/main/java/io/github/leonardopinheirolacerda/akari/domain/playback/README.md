# Domínio `playback`

## Objetivo do módulo

Orquestrar sessões de reprodução de mídia via transcode HLS sob demanda: iniciar o transcode de um arquivo de vídeo já disponível em disco (provider `LOCAL`, lido direto; ou provider remoto, já baixado no cache), permitir reposicionamento (`seek`) e troca de faixas de áudio/legenda, servir a playlist `m3u8` gerada e seus arquivos, e manter viva a sessão por heartbeat do cliente.

## Responsabilidade principal

É o maior domínio do projeto (~2326 linhas, 33 arquivos) porque concentra duas responsabilidades de peso:

- **Ciclo de vida da sessão**: criação, reposicionamento, troca de faixas, expiração por inatividade, limpeza de processo e de arquivos em disco.
- **Montagem do comando `ffmpeg`**: decidir *o que* transcodificar (perfil → estratégia → spec estruturada) e traduzir essa spec em argumentos de linha de comando reais.

A sessão (`PlaybackSession`) é 100% efêmera: não existe um "status encerrado" persistido — o estado é inferido pelos campos (pid, `processStartedAt`, `lastHeartbeatAt`) e a linha é apagada quando a sessão termina.

## Funcionalidades existentes

- Iniciar sessão de transcode (`POST /v1/playback/sessions`): resolve o arquivo em disco, valida índices de faixa, escolhe a estratégia de transcode pelo profile (`WEB`/`TV`/`MOBILE`), sobe o `ffmpeg` e persiste a sessão.
- Seek (`POST .../seek`): se o instante pedido já está dentro da janela já transcodificada (lida da playlist, com tolerância de 0.5s), não reinicia o processo; fora da janela, reinicia o `ffmpeg` do novo offset reaproveitando as faixas ativas.
- Troca de faixas (`POST .../tracks`): sempre reinicia o transcode — trocar `-map`/filtro de legenda exige novo processo `ffmpeg`, não há posição que evite o reinício.
- Servir playlist (`GET .../playlist.m3u8`) e arquivos HLS (`GET .../{file}`: segmentos, variant playlists, legendas WebVTT, init segment), com validação de nome por regex (proteção contra path traversal).
- Heartbeat (`POST .../heartbeat`): renova o TTL da sessão e devolve a validade restante.
- Varredura periódica (a cada 5s) que encerra e remove sessões sem heartbeat dentro do TTL configurado.
- Suporte a HLS de rendition única ou ABR (múltiplas resoluções, master + variant playlists), legenda soft (WebVTT, para SRT) ou burn-in (demais codecs), e aceleração de hardware de vídeo (VideoToolbox/NVENC/QSV/VAAPI) conforme preferência do admin.

## Dependências internas e externas

Dependências de saída (único sentido — ver "Observações técnicas"):

- `domain/videofile` (`VideoFileService`, `VideoFile`) — resolve o arquivo sendo reproduzido.
- `domain/mediaprobe` (`MediaProbeService`) — metadados técnicos (codecs, faixas, frame rate) usados tanto para validar índices de faixa quanto para montar o comando `ffmpeg`.
- `domain/storageprovider` (`StorageProviderClientFactory`) — resolve o path local do arquivo (via `VideoFileLocationUtils`, em `utils/`, compartilhado com `mediaprobe`).
- `domain/setting` (`SettingService`) — preferências `playback.work.base-dir`, `playback.session.heartbeat-ttl`, `playback.transcode.hardware-acceleration`.
- Indiretamente, `domain/cache` (via `VideoFileLocationUtils`, para arquivos de provider remoto).

Dependência externa: binário `ffmpeg` resolvido via `PATH` (config `akari.playback.ffmpeg.binary`, default `ffmpeg`), chamado via `ProcessBuilder`. Nenhuma validação de disponibilidade no startup — só falha na primeira sessão iniciada. **Aceito por ora, mas roadmap já desenhado** (confirmado pelo mantenedor): vai existir um readiness check central em `config/` (ex.: `/q/health/ready`) validando dependências de infra — este binário, `ffprobe` e o processo `rclone rcd` — ver `config/README.md`, `mediaprobe/README.md` e `storageprovider/provider/README.md`.

## Módulos relacionados

Nenhum outro domínio depende de `playback` — confirmado por busca textual (`grep -rl "akari.domain.playback"` fora do próprio pacote não retorna nada). É um domínio "folha" na árvore de dependências, consumido apenas pela camada HTTP (via `PlaybackResource`/contrato OpenAPI).

## Pontos de entrada

- `resource/PlaybackResource` — implementa `PlaybackApi` (gerado do `openapi.yaml`, tag `Playback`), delega 100% ao `PlaybackService`. Também usa um `@ServerResponseFilter` para adicionar o header `X-Playback-Heartbeat-Ttl-Seconds`, o `Content-Type` correto por extensão e suporte a `Range`/`206 Partial Content` (necessário para HLS nativo do Safari) — coisas que a interface gerada não expressa.
- `scheduler/PlaybackScheduler` — dispara `PlaybackService.sweepExpiredSessions()` a cada 5s.

## Fluxos importantes

**Início de sessão**: `PlaybackResource` → `PlaybackService.startPlaybackSession` → resolve `VideoFile` e path em disco → probe de mídia → valida índices de faixa → `TranscodeProfileSelector.select(profile, probe)` escolhe a `HlsTranscodeStrategy` vencedora → `strategy.buildSpec(probe)` gera a `HlsTranscodeSpec` → cria o diretório de trabalho (`<work-base-dir>/<sessionId>`) → `FfmpegClient.start` monta o comando e sobe o processo → persiste `PlaybackSession` com pid, `workDir`, `processStartedAt`.

**Seek**: lê a playlist (`stream_0.m3u8` se ABR, senão `index.m3u8`) e soma as durações `#EXTINF` (`HlsPlaylistParser`) para saber quanto já foi transcodificado; se o instante pedido cai dentro da janela `[startOffset - 0.5s, startOffset + transcodado + 0.5s]`, devolve a sessão sem reiniciar nada; senão, encerra o processo atual, limpa o workspace e sobe um novo `ffmpeg` a partir do novo offset.

**Encerramento (por reinício ou expiração)**: `ProcessTerminationService.terminate(pid, processStartedAt)` confirma que o pid ainda pertence ao processo esperado (compara o instante real de início do processo vivo com `processStartedAt`, tolerância de 10s, para não matar um processo que o SO reaproveitou) — manda SIGTERM, espera até 5s, força SIGKILL se necessário. Em seguida `PlaybackWorkspaceService.delete(workDir)` apaga recursivamente o diretório da sessão (best-effort, idempotente).

**Varredura de expiração**: `PlaybackScheduler` (a cada 5s) → `PlaybackService.sweepExpiredSessions()` lista todas as sessões e, para cada uma com `isExpired(now, ttl)` verdadeiro (sem heartbeat, ou sem criação, há mais que o TTL), encerra o processo, limpa o workspace e apaga a linha — falha em uma sessão não impede a varredura das demais.

## Arquivos críticos

- `service/PlaybackService.java` — orquestração central; todo o ciclo de vida da sessão passa por aqui.
- `client/FfmpegClient.java` — monta o comando `ffmpeg` completo (rendition única e ABR); é o arquivo mais complexo do domínio, descrito em detalhe no README de `transcode/`.
- `model/PlaybackSession.java` — entidade Panache que modela o estado efêmero da sessão.
- `service/ProcessTerminationService.java` — gerência de processo genérica, com proteção contra reaproveitamento de pid pelo SO.
- `service/PlaybackWorkspaceService.java` — limpeza best-effort do diretório de trabalho.
- `parser/HlsPlaylistParser.java` — lê quanto já foi transcodificado, decisivo para o `seek` não reiniciar sem necessidade.
- `transcode/` — ver README dedicado; contém a seleção de perfil/estratégia e os specs estruturados consumidos pelo `FfmpegClient`.

## Observações técnicas e débitos identificados

- **Processo órfão em caso de crash do app**: o pid do `ffmpeg` só é conhecido e encerrado através da linha `PlaybackSession` (no reinício ou na varredura). Se o processo da aplicação cair, o `ffmpeg` continua rodando indefinidamente — não há nenhuma rotina de "adoção"/reconciliação na inicialização que escaneie processos órfãos a partir das sessões persistidas (ou vice-versa). **Backlog real** (confirmado pelo mantenedor).
- **Workspace órfão na mesma situação**: o diretório de trabalho só é limpo por `terminateAndCleanup`, chamado em reinício ou expiração. Um crash no meio de uma sessão ativa deixa o diretório em disco permanentemente, sem rotina de limpeza na inicialização. **Backlog real** (confirmado pelo mantenedor).
- **Confirmação de posse do pid é best-effort**: `ProcessTerminationService` usa tolerância de 10s entre `processStartedAt` registrado e o início real do processo vivo; é uma mitigação razoável contra reaproveitamento de pid pelo SO, mas não é uma garantia absoluta (não há tracking por `ProcessHandle` em memória, só por pid+timestamp persistidos).
- **Concorrência entre sessões**: nada no código sincroniza sessões diferentes do mesmo `fileId` — duas sessões simultâneas sobre o mesmo arquivo rodam dois processos `ffmpeg` independentes em diretórios separados, sem controle de recurso (CPU/E/S) entre elas. **Backlog real** (confirmado pelo mantenedor).
- **Lock por sessão**: operações sobre a mesma sessão (`seek`, troca de faixas, heartbeat) não têm nenhum lock explícito além da transação do banco; duas chamadas concorrentes de `seek`/`tracks` na mesma sessão podem gerar condição de corrida entre a leitura/gravação de `ffmpegPid`/`workDir` e a criação do novo processo (não tratado no código). **Backlog real** (confirmado pelo mantenedor).
- Essas lacunas (processo e workspace órfãos, ausência de lock por sessão) são reais — nenhuma rotina de reconciliação na inicialização ou lock explícito foi encontrada no código lido. Todas confirmadas como backlog real pelo mantenedor, mesma categoria do risco geral já documentado em `ARQUITETURA_DO_SISTEMA.md`.
