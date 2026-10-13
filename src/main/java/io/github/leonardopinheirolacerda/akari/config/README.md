# config

## Objetivo do módulo
Concentrar configuração transversal da aplicação (health checks, `@ConfigMapping`, producers) que não pertence a nenhum domínio específico. Hoje contém apenas um health check.

## Responsabilidade principal
Expor sinais de infraestrutura da aplicação (ex.: liveness) para o orquestrador/runtime, sem lógica de negócio.

## Funcionalidades existentes
- **Liveness check** (`LivenessHealthCheck`): responde sempre `UP` em `/q/health/live` (exposto pelo SmallRye Health), sem consultar nenhuma dependência (banco, rclone, APIs externas). Só sinaliza que o processo está respondendo.

## Dependências internas e externas
- Externas: `org.eclipse.microprofile.health` (`HealthCheck`, `HealthCheckResponse`, `@Liveness`), `jakarta.enterprise.context.ApplicationScoped`.
- Internas: nenhuma (não importa nada de `domain/`, `exceptions/`, `utils/` ou `model/`).

## Módulos relacionados
Nenhum módulo interno depende de `config/` nem é referenciado por ele — é descoberto via anotação CDI/MicroProfile (`@Liveness` + `@ApplicationScoped`), não por import direto de outro código da aplicação.

## Pontos de entrada
`LivenessHealthCheck.call()`, invocado pelo runtime do SmallRye Health quando o endpoint `/q/health/live` é consultado.

## Fluxos importantes
Único fluxo: requisição ao endpoint de liveness → `call()` retorna `HealthCheckResponse.named("Liveness Health Check").up().build()` → runtime devolve `200 UP`.

## Arquivos críticos
- `LivenessHealthCheck.java` — único arquivo do pacote.

## Observações técnicas e débitos identificados
Não há readiness check (que consultaria dependências reais, ex.: banco) nem outra configuração transversal (`@ConfigMapping`, producers) implementada ainda, apesar de o pacote existir para esse propósito. **Backlog real, com desenho já decidido** (confirmado pelo mantenedor): vai existir um readiness check central aqui em `config/` (ex.: `/q/health/ready`), agregando a validação das dependências de infra — banco, e os binários/processos externos (`ffprobe`, `ffmpeg`, `rclone rcd`) hoje só verificados na primeira chamada real ou, no caso do rclone, por provider em `/v1/storage/providers/{id}/health`. Ver também `mediaprobe/README.md`, `playback/README.md` e `storageprovider/provider/README.md`, que documentam a mesma intenção do lado de cada dependência.
