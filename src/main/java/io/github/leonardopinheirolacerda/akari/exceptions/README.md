# exceptions

## Objetivo do módulo
Padronizar como falhas da aplicação se tornam respostas HTTP no formato `ApiError` do contrato (`openapi.yaml`), para que todo domínio reporte erro da mesma forma, sem cada resource montar sua própria resposta.

## Responsabilidade principal
`exceptions/` (raiz) define o vocabulário de erro da aplicação: uma hierarquia fechada de exceções, cada uma já amarrada a um status HTTP e a um `ApiErrorCode` (enum gerado do contrato). `exceptions/mapper/` traduz essas exceções (e outras que escapam do runtime JAX-RS/Bean Validation/Jackson) no corpo `ApiError` de fato, via `@ServerExceptionMapper` do RESTEasy Reactive.

## Funcionalidades existentes
**Hierarquia de exceções** (`AkariException` abstrata, carrega `status` + `ApiErrorCode` + mensagem + causa opcional):
- `BusinessRuleException` — `400` / `BUSINESS_RULE_VIOLATION`: input válido na forma mas que fere regra de negócio.
- `IntegrationException` — `502` / `INTEGRATION_ERROR`: sistema externo (AniList, TMDB, rclone, ffprobe) falhou; duas construtoras (com e sem `cause`).
- `IntegrationNotConfiguredException` — `503` / `INTEGRATION_NOT_CONFIGURED`: preferência obrigatória sem valor configurado.
- `RequestValidationException` — `400` / `VALIDATION_ERROR`: parâmetro de request mal formado ou fora do permitido (não é violação de contrato em si).
- `ResourceNotFoundException` — `404` / `RESOURCE_NOT_FOUND`: recurso buscado não existe.

**Mappers** (`exceptions/mapper/`):
- `AkariExceptionMapper` — traduz `AkariException` (qualquer subclasse) usando o `status()`/`code()`/mensagem que ela já carrega.
- `ConstraintViolationExceptionMapper` — traduz `ConstraintViolationException` (Bean Validation) em `400`/`VALIDATION_ERROR`, com uma entrada em `details` por violação (campo + mensagem do validador); substitui o mapper default do Quarkus, que responde fora do contrato.
- `JsonExceptionMapper` — traduz `JsonProcessingException`/`MismatchedInputException` (Jackson) em `400`/`VALIDATION_ERROR` com mensagem fixa `"JSON inválido"` (a mensagem real do Jackson expõe nomes de classe Java, por isso não é usada).
- `UnexpectedExceptionMapper` — rede de segurança: qualquer `Throwable` sem mapper mais específico vira `500`/`INTERNAL_ERROR` com mensagem fixa `"Erro inesperado no servidor"`.
- `WebApplicationExceptionMapper` — traduz erros do próprio runtime JAX-RS (`WebApplicationException`: rota inexistente, método não suportado, `Accept`/`Content-Type` não suportados etc.), preservando o status e derivando o `ApiErrorCode` por faixa (`400`→`VALIDATION_ERROR`, `404`→`RESOURCE_NOT_FOUND`, outros `4xx`→`REQUEST_ERROR`, `5xx`→`INTERNAL_ERROR`).
- `ApiErrorResponses` — helper `final`/package-private (não é um mapper), usado por todos os mappers acima para montar o `Response` com `ApiError` e logar a exceção: `5xx` em `ERROR` com stack trace completo, demais em `WARN` só com a mensagem.

## Dependências internas e externas
- Externas: `org.jboss.resteasy.reactive.server.ServerExceptionMapper` (descoberta automática pelo Quarkus REST, sem registro manual), `jakarta.validation.*`, `jakarta.ws.rs.*`, `com.fasterxml.jackson.*`, `io.quarkus.logging.Log`.
- Internas: `io.github.leonardopinheirolacerda.akari.api.dto.{ApiError, ApiErrorCode, ErrorDetail}` (DTOs gerados do contrato). `exceptions/mapper/` importa `exceptions/` (ex.: `AkariExceptionMapper` importa `AkariException`).
- **`exceptions/` (raiz) não importa nada de `exceptions/mapper/`** — confirmado: nenhum `import` de `akari.exceptions.mapper` em nenhum arquivo da raiz. O Javadoc das exceções cita os mappers (ex.: `AkariExceptionMapper`) sempre com `{@code}`, nunca `{@link}`, exatamente para não criar esse import e evitar a dependência circular. Essa é a regra de design do projeto e está corretamente seguida no código atual.

## Módulos relacionados
Consumido por praticamente todo `domain/` — resources e services de `anibridge`, `anilist`, `anime`, `cache`, `ingestion`, `mediafolder`, `mediaprobe`, `playback`, `setting`, `storageprovider`, `tmdb` e `videofile` lançam subclasses de `AkariException` (o domínio `notification` não lança nenhuma exceção deste pacote hoje). Também é usado por `utils/` — `PaginationUtils` e `VideoFileLocationUtils` lançam `RequestValidationException`/`ResourceNotFoundException`/`IntegrationNotConfiguredException` diretamente, e `RetryUtils` captura `IntegrationException` para decidir se retenta.

## Pontos de entrada
Os mappers (`@ServerExceptionMapper`) são os pontos de entrada, acionados pelo runtime JAX-RS quando uma exceção escapa de um resource: `AkariExceptionMapper.map(AkariException)`, `ConstraintViolationExceptionMapper.map(ConstraintViolationException)`, `JsonExceptionMapper.map(JsonProcessingException)`, `UnexpectedExceptionMapper.map(Throwable)`, `WebApplicationExceptionMapper.map(WebApplicationException)`.

## Fluxos importantes
1. Service/resource de um domínio lança uma subclasse de `AkariException` (ex.: `ResourceNotFoundException`) → `AkariExceptionMapper` intercepta → `ApiErrorResponses.of(status, code, message, e)` monta o `Response` e loga.
2. Bean Validation rejeita um parâmetro/DTO → `ConstraintViolationException` → `ConstraintViolationExceptionMapper` extrai o campo de cada `Path` (tratando parâmetro de query/path vs. campo de body/coleção) → `400` com `details` por violação.
3. Body não desserializa → `JsonExceptionMapper` → `400` com mensagem fixa.
4. Nenhum mapper específico casa → `UnexpectedExceptionMapper` → `500` com mensagem fixa e detalhe completo só no log.
5. Runtime JAX-RS rejeita a requisição antes de chegar ao resource (rota/método/media type) → `WebApplicationExceptionMapper` → status original preservado.

## Arquivos críticos
- `AkariException.java` — base de toda a hierarquia; qualquer novo tipo de erro de negócio nasce aqui.
- `ApiErrorResponses.java` — ponto único de montagem de resposta e política de log; qualquer mudança no formato do erro ou no nível de log passa por aqui.
- `UnexpectedExceptionMapper.java` — rede de segurança; sem ele, erros não mapeados sairiam fora do contrato.

## Observações técnicas e débitos identificados
- A separação `exceptions/` → `exceptions/mapper/` (dependência de mão única) está corretamente implementada: zero imports de `exceptions/mapper` dentro de `exceptions/`, e o Javadoc usa `{@code}` para citar os mappers, como a convenção do projeto exige.
- `utils/PaginationUtils` e `utils/VideoFileLocationUtils` lançam exceções deste pacote diretamente (fora de `domain/`), prática que o próprio Javadoc de `VideoFileLocationUtils` reconhece como precedente intencional ("um helper de `utils/` pode lançar as exceções da aplicação diretamente quando a regra de erro é a mesma pra todo chamador") — não é um débito, mas é uma exceção à ideia de que só `domain/` lança `AkariException`.
