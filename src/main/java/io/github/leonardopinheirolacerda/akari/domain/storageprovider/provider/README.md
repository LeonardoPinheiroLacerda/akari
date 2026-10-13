# storageprovider/provider

## Objetivo do módulo

Subpacote de implementação dentro de `storageprovider`, separado por responsabilidade (não por
domínio): concentra tudo que lida com o backend concreto de cada tipo de provider — a escolha
da implementação certa, a comunicação com o rclone rcd e o mecanismo de obscurecimento de
credenciais. Existe porque `storageprovider` tem múltiplos "providers" (LOCAL, MEGA) com
configs e protocolos de acesso diferentes; o resto do domínio (`service`, `resource`) não
precisa saber desses detalhes.

## Responsabilidade principal

1. Resolver, a partir do `type` de um `StorageProvider`, a implementação de
   `StorageProviderClient` correta (`StorageProviderClientFactory`).
2. Obscurecer campos sensíveis de config antes de persistir, usando o rclone como serviço de
   criptografia reversível (`ObscureFieldApplier` + anotação `@Obscure`).
3. Falar com o processo rclone rcd via REST (`RcloneClient`) e traduzir a resposta crua para o
   vocabulário de domínio (`StorageItem`, via `StorageItemMapper`).

## Funcionalidades existentes

- **Resolução de client por tipo** — `StorageProviderClientFactory.getClient`.
- **Obscurecimento de campos sensíveis** — `ObscureFieldApplier.obscureRequiredFields` +
  `@Obscure`.
- **Transporte REST com o rclone rcd** — `RcloneClient` (listagem, cópia de arquivo
  assíncrona, status de job, progresso de transferência, obscure, health check).
- **Client remoto genérico via rclone** — `RemoteStorageProviderClient` (base abstrata;
  `MegaStorageProviderClient` é a única subclasse hoje).
- **Client local puro Java NIO** — `LocalStorageProviderClient.impl` (sem rclone, sem rede).
- **Schemas de config tipados** — `config/LocalConfig` (`rootPath`), `config/MegaConfig`
  (`email`, `password` com `@Obscure`); resolvidos por `StorageProviderConfigs`.
- **Modelo de item listado** — `model/StorageItem`, com helpers de árvore (`withSyntheticRoot`,
  `sortedByDepth`, `directChildrenOf`) usados por quem consome listagens (ex.: ingestion).

## Dependências internas e externas

- Depende de `storageprovider/model/StorageProvider` (pacote-pai) e de nada fora de
  `storageprovider`.
- Externo: REST client MicroProfile (`@RegisterRestClient(configKey = "rclone")`), Jackson
  (`ObjectMapper`, `@JsonProperty`/`@JsonIgnoreProperties`), Bean Validation (`Validator`),
  reflection (`java.lang.reflect.Field`), Lombok (`@RequiredArgsConstructor` em
  `RemoteStorageProviderClient`).
- Config: `quarkus.rest-client.rclone.url` (`application.properties`), default
  `http://localhost:5572`, sobrescrita por `RCLONE_HOST`.

## Módulos relacionados

- `storageprovider/service/StorageProviderService` — único consumidor direto de
  `StorageProviderClientFactory` e `ObscureFieldApplier` dentro do próprio domínio.
- `utils/VideoFileLocationUtils` — usa `StorageProviderClientFactory` para resolver o path de um
  `VideoFile`, sem conhecer `RemoteStorageProviderClient`/`LocalStorageProviderClient`
  diretamente.
- `domain/ingestion`, `domain/cache`, `domain/playback`, `domain/mediaprobe` — consomem
  `StorageProviderClient` (a interface), nunca os tipos deste subpacote diretamente.

## Pontos de entrada

- `StorageProviderClientFactory.getClient(StorageProvider)` — ponto de entrada para obter um
  client operável.
- `ObscureFieldApplier.obscureRequiredFields(Map, Class)` — ponto de entrada para
  obscurecer+validar uma config bruta.
- `RcloneClient` — ponto de entrada de baixo nível, injetado diretamente onde a chamada ao rcd
  não passa por um `StorageProviderClient` (ex.: health check do rcd em si,
  `StorageProviderService`).

## Fluxos importantes

### Escolha de implementação: LOCAL vs MEGA/rclone

`StorageProviderClientFactory.getClient` faz um `switch` sobre `storageProvider.type`:

```java
return switch (storageProvider.type) {
    case MEGA -> new MegaStorageProviderClient(storageProvider, storageItemMapper, rcloneClient);
    case LOCAL -> new LocalStorageProviderClient(storageProvider.config);
};
```

- `MEGA` instancia `MegaStorageProviderClient`, que estende `RemoteStorageProviderClient`: toda
  operação (listar, baixar, checar conexão) delega para `RcloneClient`, montando o filesystem
  descriptor (`fs`) do rclone a partir da config (`:mega,user=<email>,pass=<password>:`) via
  `getProviderFs`. `resolveLocalPath` sempre devolve vazio — provider remoto nunca tem path
  físico direto, só cache.
- `LOCAL` instancia `LocalStorageProviderClient`, que não toca rclone nem rede: lê `rootPath` da
  config e opera com `java.nio.file.Files` (listagem, walk de árvore, `probeContentType`).
  `downloadFile` lança `UnsupportedOperationException` — local não tem o que baixar.
- A fábrica não cacheia instâncias: cada chamada cria um client novo. O `RcloneClient` injetado
  (um REST client CDI) é reaproveitado entre instâncias de `MegaStorageProviderClient` criadas na
  mesma chamada da fábrica.
- Se novos tipos de provider forem adicionados, o `switch` exaustivo sobre o enum
  `StorageProviderType` força a implementação de um novo `case` (erro de compilação caso
  contrário).

### Obscure/ObscureFieldApplier: como evita vazar credenciais

1. Um record de config (ex.: `MegaConfig`) marca com `@Obscure` os campos sensíveis em claro
   (hoje, só `password`).
2. `ObscureFieldApplier.obscureRequiredFields(config, configType)` itera, via reflection, os
   `declaredFields` de `configType` procurando `@Obscure`. Para cada campo presente e não-branco
   no mapa de config recebido, chama `POST /core/obscure` do rclone (`RcloneObscureRequest` →
   `RcloneObscureResponse`) e substitui o valor em claro pelo obscurecido no próprio mapa.
3. Depois de obscurecer, converte o mapa inteiro (`ObjectMapper.convertValue`) para o record
   `configType` e roda Bean Validation (`Validator.validate`) — garante que a config bate com o
   schema esperado (`@NotBlank`, etc.) antes de voltar.
4. O mapa retornado — já com os campos sensíveis obscurecidos — é o que
   `StorageProviderService` persiste em `StorageProvider.config`.
5. Isso é o que faz o valor nunca ser gravado em claro no banco. O obscurecimento do rclone é
   **reversível** (não é hash — o próprio rclone consegue decifrar na hora de montar o `fs`),
   então o objetivo não é criptografia forte: é evitar que a senha fique legível a olho nu em
   dumps/logs/consultas diretas ao banco.
6. Separadamente, `StorageProviderMapper` garante que o campo `config` nunca aparece no DTO de
   resposta da API — mesmo já obscurecido, a API nunca ecoa a config de volta.

### RcloneClient: comunicação com o processo rclone

- `RcloneClient` é um REST client MicroProfile (`@RegisterRestClient(configKey = "rclone")`),
  JSON sobre HTTP, mapeando 1:1 os endpoints do rclone remote control daemon (rcd):
  `/operations/list`, `/operations/copyfile`, `/job/status`, `/core/stats`, `/core/obscure`,
  `/rc/noop`.
- A URL base vem de `quarkus.rest-client.rclone.url` em `application.properties`, default
  `http://localhost:5572` (sobrescrevível por `RCLONE_HOST`).
- O rcd roda como processo local (script `rclone-rcd.sh` na raiz do projeto), não
  containerizado — propositalmente, para enxergar o mesmo filesystem do host que a app roda
  (relevante para `cache.dir`: o rclone escreve o arquivo baixado direto onde a app depois lê,
  sem precisar de volume compartilhado entre containers). O script sobe
  `rclone rcd --rc-no-auth --rc-addr 127.0.0.1:5572`, sem autenticação no rc — por isso
  `RcloneClient` não carrega nenhuma credencial de acesso ao próprio rcd, só credenciais do
  backend remoto (MEGA) dentro do `fs` de cada chamada.
- `RcloneClient` é "transporte cru": não faz retry, não traduz exceções. Quem chama
  (`RemoteStorageProviderClient`, `ObscureFieldApplier`, `StorageProviderService`) captura
  `ProcessingException`/`WebApplicationException` e embrulha em `IntegrationException` quando
  aplicável.
- Downloads (`downloadFile`) são sempre assíncronos (`_async=true`): a resposta devolve um
  `jobid`, usado depois para consultar `/job/status` (estado finalizado/sucesso) e
  `/core/stats?group=job/<jobid>` (progresso) — esse acompanhamento mora em `domain/cache`, não
  aqui.

## Arquivos críticos

- `StorageProviderClientFactory.java` — ponto único de decisão LOCAL vs MEGA.
- `processor/ObscureFieldApplier.java` — obscurecimento + validação de config.
- `client/RcloneClient.java` — contrato REST com o rcd.
- `client/RemoteStorageProviderClient.java` — lógica compartilhada de qualquer backend rclone
  (hoje só MEGA).
- `client/impl/LocalStorageProviderClient.java` — implementação 100% local, sem rclone.
- `annotation/Obscure.java` — marcador usado por reflection no `ObscureFieldApplier`.

## Observações técnicas e débitos identificados

- **Cache de sessão MEGA stale (confirmado no código):** o Javadoc de
  `RemoteStorageProviderClient.checkConnection()` documenta que o backend `mega` do rclone
  cacheia a sessão autenticada por e-mail em memória, abaixo do `fs/cache` do rclone —
  `fscache/clear` não afeta isso (testado direto no rcd pelos autores). Se uma sessão válida já
  foi estabelecida para aquele e-mail, `checkConnection()` pode devolver `true` mesmo com a
  senha atual errada, até o processo do rcd ser reiniciado. Não há opção de config no backend
  MEGA do rclone para desabilitar esse cache. Tratado como trade-off aceito, não como bug a
  corrigir — reiniciar o rcd por request não compensa.
- **Roadmap já desenhado** (confirmado pelo mantenedor): vai existir um readiness check central em
  `config/` (ex.: `/q/health/ready`) validando se o processo `rclone rcd` em si está no ar/acessível,
  além do `checkConnection()` já existente por provider. Mesma iniciativa vale para os binários
  `ffprobe` (`mediaprobe`) e `ffmpeg` (`playback`) — ver `config/README.md`.
- `RcloneClient` não tem timeout próprio configurado explicitamente neste código — depende do
  default do REST client MicroProfile/Quarkus. **Aceito** (confirmado pelo mantenedor): o default
  é suficiente, não é dívida técnica.
- `RemoteStorageProviderClient` é genérico o suficiente para outros backends do rclone (ex.:
  Google Drive, S3) bastar implementar `getProviderFs`; hoje só existe `MegaStorageProviderClient`.
  **Há intenção real** (confirmado pelo mantenedor) de adicionar outros backends rclone no futuro
  — a generalidade atual não é incidental, é preparo deliberado para essa evolução.
- O obscurecimento via `@Obscure`/reflection só age sobre campos de primeiro nível do record de
  config — não há suporte a configs aninhadas. **Aceito por enquanto** (confirmado pelo
  mantenedor): nenhum provider no horizonte precisa de config aninhada; se um dos backends rclone
  planejados (ver acima) precisar, reavaliar aí.
