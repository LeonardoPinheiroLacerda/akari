# storageprovider

## Objetivo do módulo

Cadastro e gestão dos provedores de armazenamento de onde vêm os arquivos de vídeo: `LOCAL`
(diretório no disco do servidor) e `MEGA` (remoto, acessado via rclone). Expõe CRUD de
providers, atualização de config e checagem de saúde/conectividade em
`/v1/storage/providers`.

## Responsabilidade principal

Persistir e validar a config de cada provider de acordo com o seu `type`, obscurecendo campos
sensíveis (ex.: senha do MEGA) antes de gravar, e oferecer — para o resto da aplicação — um
client (`StorageProviderClient`) capaz de listar, baixar e resolver arquivos num provider sem
quem o chama precisar saber se ele é local ou remoto.

## Funcionalidades existentes

- `POST /v1/storage/providers` — cadastra um provider, validando e obscurecendo a config pelo
  schema do `type` informado.
- `GET /v1/storage/providers` — lista paginada.
- `GET /v1/storage/providers/{providerId}` — busca por id; o DTO de resposta nunca inclui
  `config`.
- `PUT /v1/storage/providers/{providerId}/config` — substitui a config de um provider existente
  (o `type` é imutável).
- `DELETE /v1/storage/providers/{providerId}` — remove o provider.
- `GET /v1/storage/providers/{providerId}/health` — testa conectividade real (listagem da raiz
  para remoto; existência do diretório para `LOCAL`). Provider inacessível não é erro HTTP —
  responde `200` com `healthy=false`; só vira `502` se o próprio rclone rcd estiver fora do ar.
- Resolução de client por tipo de provider e obscurecimento de credenciais — ver
  `provider/README.md`.
- `StorageProviderService.existsPath` — confirma se um path existe de fato num provider,
  listando o diretório pai e procurando o path exato entre os filhos (nem rclone nem o provider
  `LOCAL` distinguem diretório vazio de inexistente na listagem direta do próprio path).

## Dependências internas e externas

- Nenhuma dependência de outro domínio (`domain/*`) — `storageprovider` não importa
  `ingestion`, `cache`, `playback`, `mediafolder` nem `mediaprobe`. É uma folha da árvore de
  domínios, consumida por eles.
- `exceptions/` (fora de `domain/`): `ResourceNotFoundException`, `IntegrationException`.
- `utils/DirectoryUtils`, `model/PageResult`.
- Externo: rclone rcd via `RcloneClient` (REST client MicroProfile, `quarkus.rest-client.rclone.url`),
  Bean Validation (`jakarta.validation`), MapStruct, Panache (persistência de `StorageProvider`).
- DTOs/interfaces gerados do contrato (`api.StorageProviderApi`, `api.dto.*`).

## Módulos relacionados

Consumido por (dependência de `storageprovider` neles, não o inverso):
- `domain/ingestion` (`IngestionService`, `ReconciliationService`) — varre/baixa arquivos do
  provider durante a ingestão.
- `domain/cache` (`CacheQueueProcessorService`) — baixa arquivos remotos pro cache local.
- `domain/playback` (`PlaybackService`) — resolve o arquivo físico para transcode.
- `domain/mediaprobe` (`MediaProbeService`, via `utils/VideoFileLocationUtils`) — resolve o
  arquivo físico para o `ffprobe`.
- `domain/mediafolder` (`MediaFolder`, `MediaFolderService`) — cada pasta de mídia pertence a um
  storage provider.
- `utils/VideoFileLocationUtils` — helper compartilhado que decide onde um `VideoFile` está
  acessível em disco (direto, se `LOCAL`; via cache, se remoto), usando
  `StorageProviderClientFactory`.

## Pontos de entrada

- `resource/StorageProviderResource` — implementa `StorageProviderApi`, uma tradução HTTP ↔
  service sem lógica própria; só adiciona o header `Location` na criação via
  `@ServerResponseFilter`.
- `service/StorageProviderService` — ponto de entrada programático para outros domínios: CRUD,
  `checkStorageProviderHealth`, `existsPath`, `findOrThrow`.
- `provider/StorageProviderClientFactory.getClient(StorageProvider)` — ponto de entrada para
  obter um `StorageProviderClient` operável, dado um provider já carregado.

## Fluxos importantes

**Cadastro (`createStorageProvider`):** recebe `type` + config em claro → resolve o record de
schema esperado (`StorageProviderConfigs.configTypeOf`) → `ObscureFieldApplier` obscurece via
rclone os campos `@Obscure` presentes e não-brancos, depois valida o mapa inteiro (Bean
Validation) contra o record → persiste `StorageProvider` com a config já obscurecida.

**Atualização de config:** mesmo fluxo de obscurecimento/validação, mas o `type` já é o
persistido (imutável) — não pode trocar o tipo do provider numa atualização de config.

**Health check:** resolve o client via `StorageProviderClientFactory` → se for
`RemoteStorageProviderClient`, primeiro chama `RcloneClient.healthCheck()` (`/rc/noop`); falha
de transporte/HTTP aí vira `IntegrationException` (502, rcd fora do ar) → depois,
independente do tipo, chama `client.checkConnection()`, que nunca lança: devolve
`healthy=true/false`.

## Arquivos críticos

- `service/StorageProviderService.java` — toda a regra de negócio do domínio.
- `model/StorageProvider.java` — entidade Panache; `config` é `Map<String,String>` persistido
  como JSON, schema dependente de `type`.
- `provider/StorageProviderClientFactory.java` — ponto de resolução LOCAL vs MEGA (detalhado em
  `provider/README.md`).
- `provider/processor/ObscureFieldApplier.java` — obscurecimento + validação de config antes de
  persistir.
- `mapper/StorageProviderMapper.java` — garante que `config` nunca vaza no DTO de resposta.

## Observações técnicas e débitos identificados

- `StorageProviderClientFactory.getClient` cria uma instância nova do client a cada chamada
  (não cacheia) — custo baixo (são POJOs), mas vale saber que não há pool/singleton por
  provider.
- `checkStorageProviderHealth` só chama `RcloneClient.healthCheck()` para providers remotos; um
  `LOCAL` nunca passa pelo rcd nessa checagem.
- A nota sobre cache de sessão MEGA stale (ver `provider/README.md`) afeta diretamente o
  resultado de `checkStorageProviderHealth` para providers `MEGA` — é um trade-off aceito do
  projeto, documentado no Javadoc de `RemoteStorageProviderClient.checkConnection()`.
