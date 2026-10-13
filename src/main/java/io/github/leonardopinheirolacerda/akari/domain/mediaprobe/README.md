# mediaprobe

## Objetivo do módulo

Extrai metadados técnicos (container e streams de vídeo/áudio/legenda) de um arquivo de vídeo
já disponível em disco, executando `ffprobe` sob demanda. Expõe `GET
/v1/mediaprobe/files/{fileId}`.

## Responsabilidade principal

Dado o id de um `VideoFile` do catalog, resolver onde o arquivo físico está acessível (direto,
se o provider for `LOCAL`; via cache local, se for remoto e o download já tiver terminado),
invocar `ffprobe -show_format -show_streams` sobre ele e traduzir a saída JSON crua para o DTO
de resposta do contrato (`MediaInfoResponse`). É stateless: nada é persistido, cada chamada
roda o probe do zero.

## Funcionalidades existentes

- `GET /v1/mediaprobe/files/{fileId}` — probe completo: metadados do container
  (`MediaContainer`: formato, duração, tamanho, bit rate) e listas de `VideoTrack`,
  `AudioTrack`, `SubtitleTrack`, particionadas pelo `codec_type` do ffprobe.
- Parsing tolerante de valores crus do ffprobe (números vêm como string no JSON): conversão
  para `Double`/`Long`/`Integer`, cálculo de frame rate a partir da fração `avg_frame_rate`,
  leitura de tags (`language`/`title`) e flags de disposition (`default`/`forced`/
  `hearing_impaired`) — tudo com fallback `null`/`false` em vez de lançar, em
  `parser/FfprobeValueParser`.
- Execução do binário `ffprobe` com timeout configurável e saída redirecionada para arquivo
  temporário (não para o pipe de stdout) — ver seção de fluxos.

## Dependências internas e externas

- `domain/setting/service/SettingService` — resolve a preferência `cache.dir`, necessária para
  localizar o arquivo de um provider remoto no cache local.
- `domain/storageprovider/provider/StorageProviderClientFactory` — resolve o client do provider
  para localizar o arquivo de um provider `LOCAL`.
- `domain/videofile/model/VideoFile`, `domain/videofile/service/VideoFileService` — busca o
  `VideoFile` pelo `fileId` do catalog.
- `utils/VideoFileLocationUtils` — helper compartilhado (também usado por `playback`) que
  decide o path físico a partir do `VideoFile`, do factory e do `cache.dir`.
- `exceptions/` (fora de `domain/`): `ResourceNotFoundException`,
  `IntegrationNotConfiguredException`, `IntegrationException`.
- Externo: binário `ffprobe` via `ProcessBuilder` (config `akari.mediaprobe.ffprobe.binary`,
  default `ffprobe` resolvido via `PATH`; `akari.mediaprobe.ffprobe.timeout`, default `PT30S`),
  Jackson (`ObjectMapper`), MapStruct.

## Módulos relacionados

- `domain/playback` (`PlaybackService`) — único consumidor de `mediaprobe` hoje; usa os
  metadados extraídos (ex.: streams disponíveis) para decidir o transcode.
- `domain/storageprovider` — fornece o client que resolve o arquivo físico (não há dependência
  no sentido inverso).
- `domain/videofile`, `domain/setting` — fornecem o `VideoFile` e a preferência `cache.dir`
  respectivamente.

## Pontos de entrada

- `resource/MediaProbeResource` — implementa `MediaProbeApi`, tradução HTTP ↔ service sem
  lógica própria.
- `service/MediaProbeService.getMediaProbe(Integer fileId)` — ponto de entrada programático
  (usado por `PlaybackService`).

## Fluxos importantes

**`getMediaProbe(fileId)`:**
1. `VideoFileService.findOrThrow(fileId)` — busca o `VideoFile` no catalog; `404` se não
   existir.
2. `VideoFileLocationUtils.resolvePath` — resolve o `Path` físico: direto via
   `StorageProviderClientFactory` se `LOCAL` (`404` se o arquivo não existir mais em disco); via
   `VideoFileCache` (status `COMPLETED`) sob `cache.dir` se remoto (`404` se ainda não terminou
   de baixar; `503`/`IntegrationNotConfiguredException` se `cache.dir` não estiver configurado).
3. `FfprobeClient.probe(path)` — roda o processo `ffprobe`, com a saída redirecionada para um
   arquivo temporário (`Files.createTempFile`) em vez de lida do stdout pelo pipe: mídia com
   muitas faixas gera JSON maior que o buffer do pipe (~64KB) e ler só depois do `waitFor()`
   travaria (processo bloqueia escrevendo, ninguém drena, timeout estoura). Com arquivo,
   `waitFor()` funciona para qualquer tamanho de saída; o arquivo temporário é sempre apagado no
   `finally`.
4. Falhas do `ffprobe` (binário ausente, timeout, exit code não-zero, JSON inválido,
   interrupção) viram `FfprobeException` — um sinal interno, não uma `AppException` — sempre
   capturado pelo `MediaProbeService` e reembrulhado em `IntegrationException` (502),
   preservando a mensagem específica da causa.
5. `MediaProbeMapper.toResponse` converte `FfprobeOutput` para `MediaInfoResponse`: o container
   mapeia 1:1 com conversão de tipo; os `streams` (lista única, mistura vídeo/áudio/legenda) são
   particionados em três listas por `codecType`, cada item convertido pelo método `@Named`
   correspondente, delegando parsing não-trivial para `FfprobeValueParser`.

## Arquivos críticos

- `service/MediaProbeService.java` — orquestra resolução de arquivo + probe + mapeamento.
- `client/FfprobeClient.java` — execução do processo `ffprobe` e parsing do JSON de saída.
- `mapper/MediaProbeMapper.java` — conversão para o DTO do contrato, incluindo partição de
  streams por tipo.
- `parser/FfprobeValueParser.java` — todas as conversões de valor cru (string→número, frame
  rate, tags, disposition).
- `client/FfprobeException.java` — sinal interno de falha do ffprobe, sempre reembrulhado pelo
  service.

## Observações técnicas e débitos identificados

- Domínio stateless e sem efeitos colaterais no banco — nenhuma tabela própria.
- Depende de um binário externo (`ffprobe`) presente no `PATH` do ambiente onde a app roda;
  nenhuma validação de disponibilidade acontece no startup, só na primeira chamada. **Aceito por
  ora, mas roadmap já desenhado** (confirmado pelo mantenedor): vai existir um readiness check
  central em `config/` (ex.: `/q/health/ready`) validando dependências de infra (este binário,
  `ffmpeg`, processo `rclone rcd`) — ver `config/README.md`, `playback/README.md` (ffmpeg) e
  `storageprovider/provider/README.md` (rclone).
- `FfprobeValueParser` fica deliberadamente fora do mapper porque o MapStruct trataria métodos
  `default` com assinatura igual (ex.: duas conversões `String→Double`) como candidatos
  ambíguos se não fossem qualificados por nome — mesmo padrão citado no Javadoc do
  `MediaProbeMapper` em relação ao `TmdbMapper`/`TmdbImageUtils`.
- `avgFrameRate` captura só `avg_frame_rate` do ffprobe (não `r_frame_rate`) porque é a única
  fonte que o contrato usa para `frameRate` — qualquer necessidade futura de frame rate real
  (não médio) exigiria estender `FfprobeStream`.
