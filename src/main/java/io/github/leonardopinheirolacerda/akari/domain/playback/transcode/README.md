# Subpacote `transcode`

## Objetivo do módulo

Decidir **o que** transcodificar — não o processo em si. Dado um `Profile` (escolhido pelo cliente) e o probe técnico do vídeo (`MediaInfoResponse`), produz uma `HlsTranscodeSpec` estruturada (decisões de vídeo, áudio e empacotamento HLS) que o `FfmpegClient` (em `client/`) traduz em argumentos de linha de comando. Nada aqui sobe processo ou sabe de `workDir`/arquivo de entrada — isso é contexto de runtime resolvido pelo `PlaybackService`.

## Responsabilidade principal

Implementar o padrão "regra como código": cada estratégia de transcode (`HlsTranscodeStrategy`) é uma regra "condição → consequência" descoberta automaticamente pelo CDI. Para um profile e um vídeo, avaliam-se as estratégias que pertencem a esse profile; a de maior nota (`score`) vence e gera a spec.

## Funcionalidades existentes

- `Profile` (enum `WEB`, `TV`, `MOBILE`) — agrupa estratégias e é a fonte da verdade de quais profiles existem.
- `HlsTranscodeStrategy` (interface) — contrato de estratégia: `profiles()` (a quais profiles pertence), `score(probe)` (nota de aderência; `<= 0` = abstenção) e `buildSpec(probe)` (monta a `HlsTranscodeSpec` quando vencedora).
- `TranscodeStrategyCatalog` — injeta `Instance<HlsTranscodeStrategy>` (todos os beans CDI descobertos) e filtra por profile; adicionar uma estratégia nova é só criar a classe, sem registro manual.
- `TranscodeProfileSelector` — para um profile+probe, percorre `catalog.strategiesFor(profile)`, pega a de maior `score` estritamente positivo (empate resolvido pela ordem de descoberta do CDI, não por critério explícito) e devolve `Optional.empty()` se nenhuma pontuar.
- `FullTranscodeHlsStrategy` — **única estratégia concreta existente hoje**. Serve aos três profiles (`WEB`, `TV`, `MOBILE`) com nota fixa `1` (fallback de qualquer especificidade). Sempre reencoda vídeo em H.264 (preset `VERYFAST`, GOP de 2s, sem rate control explícito) e áudio em AAC (160kbps), empacotando em HLS/TS de rendition única (sem ladder ABR), playlist tipo `EVENT`, segmento de 4s.
- `transcode/spec/` — os ~11 tipos de spec estruturada (detalhados abaixo).

## Dependências internas e externas

- Depende de `api.dto.MediaInfoResponse` (DTO gerado do contrato, usado como probe de entrada).
- É consumido por `client/FfmpegClient` (traduz `HlsTranscodeSpec` em comando) e por `service/PlaybackService` (que chama `TranscodeProfileSelector.select` e guarda o nome do `Profile` vencedor na sessão).
- Nenhuma estratégia aqui acessa `domain/mediaprobe` diretamente — recebe o probe já resolvido via parâmetro.

## Módulos relacionados

- `client/FfmpegClient` — consumidor direto da `HlsTranscodeSpec`; é quem de fato decide o encoder (`libx264` vs `h264_videotoolbox` etc. — ver `HwAccel`) e monta `-map`/`-c:v`/`-c:a`/muxer HLS.
- `client/model/SubtitlePlan`/`SubtitleMode` — resolvidos pelo `FfmpegClient`, não pela estratégia: o tratamento de legenda (soft WebVTT para SRT, burn-in para o resto) é decidido pelo formato da faixa, independente do profile/estratégia escolhido.
- `service/PlaybackService` — ponto único de chamada de `TranscodeProfileSelector`; é quem lança `BusinessRuleException` se `select` devolver vazio (nenhuma estratégia aplicável).

## Pontos de entrada

- `TranscodeProfileSelector.select(Profile, MediaInfoResponse)` — único ponto de entrada externo do subpacote (chamado por `PlaybackService.resolveSpec`, usado tanto no início de sessão quanto no `restart` de seek/troca de faixas).

## Fluxos importantes

**Seleção de estratégia**: `PlaybackService` resolve o `Profile` a partir do texto do request (`Profile.valueOf`, maiúsculas) → chama `transcodeProfileSelector.select(profile, mediaInfo)` → `TranscodeProfileSelector` pede ao `TranscodeStrategyCatalog` as estratégias daquele profile → calcula `score(probe)` de cada uma → a de maior nota (`> 0`) vence; em caso de nenhuma pontuar, `Optional.empty()` → `PlaybackService` lança `BusinessRuleException` (400). Hoje, com uma única estratégia (`FullTranscodeHlsStrategy`, nota fixa `1`, aplicável a todos os profiles), a seleção sempre resolve para ela — **não há diferenciação real de comportamento entre `WEB`/`TV`/`MOBILE` no código atual**, apesar da arquitetura já suportar múltiplas estratégias competindo por nota.

**Da spec ao comando**: a `HlsTranscodeSpec` vencedora (`video`/`audio`/`hls`) chega ao `FfmpegClient.buildCommand` via `FfmpegStartParams`. O `FfmpegClient` decide a ladder de resolução (`video.resolutions()`, ordenada da maior para a menor — vazia/única = rendition simples, múltiplas = ABR com master+variant playlists), calcula o GOP em frames a partir de `gopSeconds` e do frame rate do probe (ajustado para caber um número inteiro de vezes no `segmentSeconds` do HLS), resolve o plano de legenda pelo codec da faixa escolhida (SRT → soft/WebVTT, resto → burn-in com compensação de PTS quando há seek), escolhe o encoder pelo par `VideoCodec`+`HwAccel` (cai no encoder de software quando o backend de hardware não suporta o codec, ex.: AV1 no VideoToolbox) e monta os argumentos na ordem esperada pelo ffmpeg: opções globais → seek → entrada → mapeamento de faixas/codecs → muxer HLS.

**Extensão (como adicionar uma estratégia nova)**: criar uma classe `@ApplicationScoped` implementando `HlsTranscodeStrategy`, declarar os profiles em `profiles()`, uma lógica de `score(probe)` que avalie a especificidade (ex.: pontuar mais alto para resoluções 4K, HDR, codecs específicos) e `buildSpec(probe)` com a spec correspondente — nenhum registro central precisa ser tocado, o `TranscodeStrategyCatalog` descobre via CDI automaticamente.

## Arquivos críticos

- `TranscodeProfileSelector.java` — algoritmo de seleção (maior nota estritamente positiva, empate = ordem de descoberta CDI).
- `TranscodeStrategyCatalog.java` — ponto de descoberta CDI; qualquer bean `HlsTranscodeStrategy` novo entra automaticamente.
- `HlsTranscodeStrategy.java` — contrato que toda estratégia nova deve seguir.
- `FullTranscodeHlsStrategy.java` — única implementação real hoje; é o comportamento efetivo do sistema para todos os profiles.
- `spec/HlsTranscodeSpec.java` — o "contrato de dados" entre estratégia e `FfmpegClient`.
- `spec/Resolution.java` — define a ladder ABR possível (2160p → 360p) e decide, pelo tamanho do `Set`, se a sessão é ABR ou rendition única.
- `spec/HwAccel.java` — resolvido fora da spec (preferência do admin, não da estratégia), mas acoplado ao `VideoCodec` escolhido na hora de decidir o encoder.

## Observações técnicas e débitos identificados

- **Apenas uma estratégia concreta implementada**: a arquitetura de `score`/catálogo/seleção está pronta para múltiplas estratégias por profile, mas atualmente só existe `FullTranscodeHlsStrategy` (fallback, nota 1, sem ladder ABR, sem diferenciação por profile). O profile informado pelo cliente (`WEB`/`TV`/`MOBILE`) hoje não altera nenhum parâmetro de transcode na prática — é um ponto de extensão, não uma lacuna de bug, mas vale registrar que o comportamento é uniforme até que novas estratégias sejam adicionadas.
- **Critério de desempate por ordem de descoberta do CDI**: o próprio Javadoc de `TranscodeProfileSelector` admite que o empate no topo é resolvido "por ora" pela ordem de descoberta do CDI — não é uma ordem determinística garantida pela especificação CDI. **Aceito por ora** (confirmado pelo mantenedor): só existe uma estratégia hoje, então o empate nunca ocorre na prática; definir um critério explícito fica para quando houver 2+ estratégias conflitantes.
- ~~Import não utilizado em `HlsTranscodeSpec`~~ — corrigido: `import lombok.Builder` removido (a anotação não era usada no arquivo).
- **AV1 sem aceleração de hardware em VideoToolbox**: tratado explicitamente no `FfmpegClient.encoder` (cai para `libsvtav1`, software), não é uma lacuna — é uma decisão de fallback documentada no código.
- **Hardsub em ABR multiplica custo de encode**: o próprio código loga um aviso (`FfmpegClient.appendAbr`) de que o filtro de burn-in roda em cada rendition da ladder — custo sabido e aceito, não uma lacuna.
