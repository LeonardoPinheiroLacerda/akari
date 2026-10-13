# model (raiz)

## Objetivo do módulo
Abrigar tipos genéricos cross-domínio que não têm um dono de domínio único — pela convenção do projeto, um tipo só deveria entrar aqui se usado por 2 ou mais domínios.

## Responsabilidade principal
Fornecer formas de dados reutilizáveis entre domínios: um envelope de página genérico (`PageResult`). Não tem comportamento além de ser um `record` — não há lógica de negócio aqui.

## Funcionalidades existentes
- **`PageResult<T>`**: `record(data, page, size, totalElements, totalPages)` — página genérica de resultado de consulta Panache. Cada domínio converte para o envelope de página do contrato (ex.: `VideoFilePage`) via seu próprio `*Mapper`.

## Dependências internas e externas
- `PageResult` depende só de `java.util.List`.
- Não importa `exceptions/`, `config/`, `utils/` ou qualquer `domain/`.

## Módulos relacionados
- `PageResult` é usado por 6 domínios distintos: `anime`, `cache`, `mediafolder`, `notification`, `storageprovider` e `videofile` (nos respectivos `model`/`mapper`/`service` de listagem paginada). Isso está de acordo com a convenção de 2+ domínios.

## Pontos de entrada
Não há pontos de entrada no sentido de endpoint/scheduler — é um tipo de dado instanciado por quem o usa (os `*Mapper`/`service` de paginação de cada domínio).

## Fluxos importantes
- **Paginação**: um `service` de domínio monta um `PageResult<T>` a partir de uma consulta Panache paginada; o `*Mapper` do domínio converte esse `PageResult` genérico no DTO de página específico do contrato.

## Arquivos críticos
- `PageResult.java` — tocado por qualquer mudança no formato de paginação cross-domínio.

## Observações técnicas e débitos identificados
- `PageResult` cumpre a convenção com folga (6 domínios); é o único tipo neste pacote.
