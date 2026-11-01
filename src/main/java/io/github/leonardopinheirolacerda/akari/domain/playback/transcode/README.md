# Subpacote `transcode`

## Objetivo do módulo

Decidir **o que** transcodificar — não o processo em si. Dado um `Profile` (escolhido pelo cliente) e o probe técnico do vídeo (`MediaInfoResponse`), produz uma `HlsTranscodeSpec` estruturada (decisões de vídeo, áudio e empacotamento HLS) que o `FfmpegClient` (em `client/`) traduz em argumentos de linha de comando. Nada aqui sobe processo ou sabe de `workDir`/arquivo de entrada — isso é contexto de runtime resolvido pelo `PlaybackService`.

## Responsabilidade principal

Implementar o padrão "regra como código": cada estratégia de transcode (`HlsTranscodeStrategy`) é uma regra "condição → consequência" descoberta automaticamente pelo CDI. Para um profile e um vídeo, avaliam-se as estratégias que pertencem a esse profile; a de maior nota (`score`) vence e gera a spec.

## Funcionalidades existentes

- `Profile` (enum `WEB`, `TV`, `MOBILE`, `GOOGLE_TV_STREAMER`) — agrupa estratégias e é a fonte da verdade de quais profiles existem.
- `HlsTranscodeStrategy` (interface) — contrato de estratégia: `profiles()` (a quais profiles pertence), `score(probe)` (nota de aderência; `<= 0` = abstenção) e `buildSpec(probe)` (monta a `HlsTranscodeSpec` quando vencedora).
- `TranscodeStrategyCatalog` — injeta `Instance<HlsTranscodeStrategy>` (todos os beans CDI descobertos) e filtra por profile; adicionar uma estratégia nova é só criar a classe, sem registro manual.
- `TranscodeProfileSelector` — para um profile+probe, percorre `catalog.strategiesFor(profile)`, pega a de maior `score` estritamente positivo (empate resolvido pela ordem de descoberta do CDI, não por critério explícito) e devolve `Optional.empty()` se nenhuma pontuar.
- `FullTranscodeHlsStrategy` — estratégia genérica, serve aos profiles `WEB`, `TV` e `MOBILE` com nota fixa `1` (fallback de qualquer especificidade). Sempre reencoda vídeo em H.264 (preset `VERYFAST`, GOP de 2s, sem rate control explícito) e áudio em AAC (160kbps), empacotando em HLS/TS de rendition única (sem ladder ABR), playlist tipo `EVENT`, segmento de 4s.
- `GoogleTvStreamerHlsStrategy` — estratégia exclusiva do profile `GOOGLE_TV_STREAMER`: H.264 até o nível 5.2, HEVC Main/Main10 até o nível 5.1 ou AV1 Main até o nível 5.1 (máx. 4K/60fps). Prefere remux (`COPY`) quando a fonte já está em H.264/HEVC/AV1 8 ou 10-bit (`yuv420p`/`yuv420p10le`), dentro de 3840x2160/60fps e com SAR quadrada; senão cai pra encode em HEVC (CRF 20/teto 20000kbps, preset `VERYFAST`, GOP de 2s). Áudio sempre declarado `COPY`-preferido em AAC (160kbps) — o `FfmpegClient` confirma contra a faixa de áudio realmente selecionada. Empacota em HLS/fMP4 (HEVC exige fMP4 no padrão de autoria HLS da Apple). Nota fixa `1` — hoje é a única estratégia do profile.
- `transcode/spec/` — os ~11 tipos de spec estruturada (detalhados abaixo).

## Dependências internas e externas

- Depende de `api.dto.MediaInfoResponse` (DTO gerado do contrato, usado como probe de entrada).
- É consumido por `client/FfmpegClient` (traduz `HlsTranscodeSpec` em comando) e por `service/PlaybackService` (que chama `TranscodeProfileSelector.select` e guarda o nome do `Profile` vencedor na sessão).
- Nenhuma estratégia aqui acessa `domain/mediaprobe` diretamente — recebe o probe já resolvido via parâmetro.

## Módulos relacionados

- `client/FfmpegClient` — consumidor direto da `HlsTranscodeSpec`; é quem de fato decide o encoder (`libx264` vs `h264_videotoolbox` etc. — ver `HwAccel`) e monta `-map`/`-c:v`/`-c:a`/muxer HLS.
- `client/model/SubtitlePlan`/`SubtitleMode` — resolvidos pelo `FfmpegClient`, não pela estratégia: o tratamento de legenda (soft WebVTT para SRT; burn-in de texto via libass para outros formatos de texto, ex. ASS; burn-in de imagem via overlay do stream decodificado para PGS/VobSub/DVB) é decidido pelo formato da faixa, independente do profile/estratégia escolhido.
- `service/PlaybackService` — ponto único de chamada de `TranscodeProfileSelector`; é quem lança `BusinessRuleException` se `select` devolver vazio (nenhuma estratégia aplicável).

## Pontos de entrada

- `TranscodeProfileSelector.select(Profile, MediaInfoResponse)` — único ponto de entrada externo do subpacote (chamado por `PlaybackService.resolveSpec`, usado tanto no início de sessão quanto no `restart` de seek/troca de faixas).

## Fluxos importantes

**Seleção de estratégia**: `PlaybackService` resolve o `Profile` (enum interno deste subpacote, distinto do `TranscodeProfile` gerado do contrato — `resolveProfile` converte pelo nome, `Profile.valueOf(dto.getProfile().name())`, já que os dois têm as mesmas constantes `WEB`/`TV`/`MOBILE`/`GOOGLE_TV_STREAMER`) → chama `transcodeProfileSelector.select(profile, mediaInfo)` → `TranscodeProfileSelector` pede ao `TranscodeStrategyCatalog` as estratégias daquele profile → calcula `score(probe)` de cada uma → a de maior nota (`> 0`) vence; em caso de nenhuma pontuar, `Optional.empty()` → `PlaybackService` lança `BusinessRuleException` (400). `WEB`/`TV`/`MOBILE` resolvem sempre para `FullTranscodeHlsStrategy` (única estratégia desses profiles, nota fixa `1`) — **não há diferenciação real de comportamento entre eles no código atual**. `GOOGLE_TV_STREAMER` resolve pra `GoogleTvStreamerHlsStrategy` (cap 4K/H.264+HEVC+AV1), única estratégia do profile.

**Da spec ao comando**: a `HlsTranscodeSpec` vencedora (`video`/`audio`/`hls`) chega ao `FfmpegClient.buildCommand` via `FfmpegStartParams`. O `FfmpegClient` decide a ladder de resolução (`video.resolutions()`, ordenada da maior para a menor — vazia/única = rendition simples, múltiplas = ABR com master+variant playlists), calcula o GOP em frames a partir de `gopSeconds` e do frame rate do probe (ajustado para caber um número inteiro de vezes no `segmentSeconds` do HLS), sempre corrige a SAR pra pixel quadrado (`scale=trunc(iw*sar/2)*2:ih,setsar=1`, antes de qualquer downscale ou burn — vídeo com pixel já quadrado sai inalterado), resolve o plano de legenda pelo codec da faixa escolhida (SRT → soft/WebVTT; outros formatos de texto → burn-in via libass, com compensação de PTS quando há seek, porque o filtro lê a legenda direto do arquivo com os tempos originais; PGS/VobSub/DVB → burn-in via overlay do próprio stream de legenda decodificado — sem precisar da mesma compensação de PTS, porque vem do mesmo demuxer/seek do vídeo, e escalado pra geometria final do vídeo antes de sobrepor, nunca esticado depois), escolhe o encoder pelo par `VideoCodec`+`HwAccel` (cai no encoder de software quando o backend de hardware não suporta o codec, ex.: AV1 no VideoToolbox) e monta os argumentos na ordem esperada pelo ffmpeg: opções globais (incluindo `-hide_banner -loglevel warning -nostats`, pra manter o stderr capturado pelo `TranscodeProcessRegistry` livre de ruído rotineiro) → seek → entrada → mapeamento de faixas/codecs → muxer HLS.

**`TranscodeMode.COPY` como preferência, não garantia**: `GoogleTvStreamerHlsStrategy` pode declarar `COPY` tanto pro vídeo quanto pro áudio, mas o `FfmpegClient` só efetiva o remux quando é seguro — senão cai pra encode usando os campos de encode da própria spec (por isso toda estratégia que usa `COPY` precisa preenchê-los mesmo preferindo copy). Pro vídeo, cede pra encode se a legenda selecionada exigir burn-in (texto ou imagem) — stream copy não decodifica pra aplicar o filtro. Pro áudio, cede pra encode se a faixa de áudio *realmente* selecionada (`audioTrackIndex`) não estiver de fato no codec esperado, ou — no caso de AAC — não tiver layout de canais conhecido (`channel_layout` ausente ou `unknown` no probe, comum em fontes DTS-HD MA com layout fora do padrão, ex. `5.1(side)`): copiar esse AAC repetiria um Program Config Element no `esds` que o parser de MP4 do Chromium rejeita no `init.mp4`. A estratégia decide `COPY` avaliando o probe inteiro, sem saber qual faixa o cliente vai selecionar, então essa confirmação só pode ser feita no `FfmpegClient`, que tem a seleção real. No encode de AAC sem downmix explícito (`channels()` nulo), o `FfmpegClient` sempre normaliza o layout via `-af aformat=channel_layouts=7.1|5.1|stereo|mono` — layouts fora dessa lista (como `5.1(side)`) saem como `unknown` sem essa normalização.

**Extensão (como adicionar uma estratégia nova)**: criar uma classe `@ApplicationScoped` implementando `HlsTranscodeStrategy`, declarar os profiles em `profiles()`, uma lógica de `score(probe)` que avalie a especificidade (ex.: pontuar mais alto para resoluções 4K, HDR, codecs específicos) e `buildSpec(probe)` com a spec correspondente — nenhum registro central precisa ser tocado, o `TranscodeStrategyCatalog` descobre via CDI automaticamente.

## Arquivos críticos

- `TranscodeProfileSelector.java` — algoritmo de seleção (maior nota estritamente positiva, empate = ordem de descoberta CDI).
- `TranscodeStrategyCatalog.java` — ponto de descoberta CDI; qualquer bean `HlsTranscodeStrategy` novo entra automaticamente.
- `HlsTranscodeStrategy.java` — contrato que toda estratégia nova deve seguir.
- `FullTranscodeHlsStrategy.java` — estratégia dos profiles `WEB`/`TV`/`MOBILE`, sem diferenciação entre eles.
- `GoogleTvStreamerHlsStrategy.java` — estratégia do profile `GOOGLE_TV_STREAMER`, com cap de resolução em 4K, codec H.265 (fallback de encode) e preferência por remux (H.264/HEVC/AV1) quando a fonte já se encaixa.
- `client/FfmpegClient.java` (`appendVideo`/`appendAudio`) — valida se um `TranscodeMode.COPY` preferido é de fato seguro (legenda sem burn-in pro vídeo; faixa de áudio selecionada já no codec esperado e, pra AAC, com layout de canais conhecido) antes de efetivar o remux, caindo pra encode senão; no encode de AAC sem downmix explícito, normaliza o layout de canais via `-af aformat` pra evitar PCE no `esds`.
- `spec/HlsTranscodeSpec.java` — o "contrato de dados" entre estratégia e `FfmpegClient`.
- `spec/Resolution.java` — define a ladder ABR possível (2160p → 360p) e decide, pelo tamanho do `Set`, se a sessão é ABR ou rendition única.
- `spec/HwAccel.java` — resolvido fora da spec (preferência do admin, não da estratégia), mas acoplado ao `VideoCodec` escolhido na hora de decidir o encoder.

## Observações técnicas e débitos identificados

- **Sem diferenciação entre `WEB`/`TV`/`MOBILE`**: os três resolvem para a mesma `FullTranscodeHlsStrategy` (fallback, nota 1, sem ladder ABR). O profile informado pelo cliente hoje só diferencia comportamento para `GOOGLE_TV_STREAMER`; para os demais é um ponto de extensão, não uma lacuna de bug.
- **Critério de desempate por ordem de descoberta do CDI**: o próprio Javadoc de `TranscodeProfileSelector` admite que o empate no topo é resolvido "por ora" pela ordem de descoberta do CDI — não é uma ordem determinística garantida pela especificação CDI. Aceito por ora: nenhum profile tem hoje duas estratégias concorrentes, então o empate nunca ocorre na prática; definir um critério explícito fica para quando houver 2+ estratégias conflitantes no mesmo profile.
- **AV1 sem aceleração de hardware em VideoToolbox**: tratado explicitamente no `FfmpegClient.encoder` (cai para `libsvtav1`, software), não é uma lacuna — é uma decisão de fallback documentada no código.
- **Hardsub em ABR multiplica custo de encode**: tanto o burn-in de texto quanto o de imagem repetem o filtro (decodificação de legenda incluída) em cada rendition da ladder — custo sabido e aceito, não uma lacuna. O aviso de log (`FfmpegClient.appendAbr`) só existe hoje pro caso de texto.
- **Caminho de arquivo escapado só no burn-in de texto**: o filtro `subtitles` do libass embute o caminho bruto do arquivo de entrada na expressão do filtergraph, então caracteres como `:`/`[`/`]` (comuns em nome de release de fansub) quebrariam o parse se não fossem escapados (`FfmpegClient.escapeFilterPath`). O burn-in de imagem não precisa disso — referencia a legenda por `0:s:N`, nunca pelo nome do arquivo.
