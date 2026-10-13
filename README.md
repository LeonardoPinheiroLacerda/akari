# akari

Backend de catálogo e reprodução de vídeo com foco em anime: ingere o conteúdo de storage
providers (disco local ou MEGA via rclone), organiza em um catálogo de pastas/arquivos, enriquece
com metadata de anime (AniList + TMDB) e serve a reprodução via transcode HLS sob demanda, com
cache local de arquivos remotos.

Módulo único Quarkus, contract-first: `src/main/resources/META-INF/openapi.yaml` gera as
interfaces JAX-RS e os DTOs da API (nunca escritos à mão). Veja a documentação completa:

- [`docs/OBJETIVO_DO_SISTEMA.md`](docs/OBJETIVO_DO_SISTEMA.md) — propósito, problemas resolvidos,
  fluxos de negócio, atores.
- [`docs/ARQUITETURA_DO_SISTEMA.md`](docs/ARQUITETURA_DO_SISTEMA.md) — padrões, convenções,
  dependências entre domínios, riscos técnicos conhecidos.
- `README.md` dentro de cada pacote em
  `src/main/java/io/github/leonardopinheirolacerda/akari/` (um por domínio em `domain/`, e um em
  cada pacote transversal — `config/`, `exceptions/`, `model/`, `utils/`) e em
  `src/main/resources/db/` (migrations Flyway).
- `.claude/skills/code-style/SKILL.md` — convenções de código Java do projeto.

Este projeto usa Quarkus, o Supersonic Subatomic Java Framework. Para aprender mais sobre o
Quarkus, visite <https://quarkus.io/>.

## Dependências externas para rodar localmente

- **PostgreSQL** — `docker-compose up -d` sobe só o banco (porta `5432`, credenciais padrão
  `akari`/`akari`, sobrescrevíveis com `DB_USERNAME`/`DB_PASSWORD`). Migrations Flyway em
  `src/main/resources/db/` aplicam no boot da aplicação.
- **rclone rcd** — necessário só para usar um storage provider `MEGA` (obscurecimento de
  credenciais e acesso remoto). Suba com `./rclone-rcd.sh`: ele roda **fora** de container, de
  propósito, para enxergar o mesmo filesystem do host que a aplicação (relevante para `cache.dir`).
  Escuta em `127.0.0.1:5572`, o endereço padrão esperado por `quarkus.rest-client.rclone.url`.
- **ffprobe / ffmpeg** — binários resolvidos via `PATH` por padrão; configuráveis por caminho
  absoluto via `akari.mediaprobe.ffprobe.binary` e `akari.playback.ffmpeg.binary`.
- Nenhuma credencial de integração externa (AniList é público; TMDB exige token) é configurada por
  variável de ambiente ou arquivo — tudo é feito em runtime via `PUT /v1/settings/{key}`
  (ex.: `tmdb.api-key`). Preferência obrigatória sem valor faz os fluxos que dependem dela responder
  `503`.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/akari-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.

## Related Guides

- REST ([guide](https://quarkus.io/guides/rest)): Build RESTful web services and APIs using Jakarta REST (formerly JAX-RS)
- Flyway ([guide](https://quarkus.io/guides/flyway)): Handle your database schema migrations
- REST Client ([guide](https://quarkus.io/guides/rest-client)): Type-safe HTTP client for consuming REST APIs using MicroProfile REST Client
- SmallRye Fault Tolerance ([guide](https://quarkus.io/guides/smallrye-fault-tolerance)): Build fault-tolerant network services
- Scheduler ([guide](https://quarkus.io/guides/scheduler)): Schedule recurring tasks and periodic jobs using cron expressions or fixed intervals
- SmallRye Health ([guide](https://quarkus.io/guides/smallrye-health)): Monitor service health
- JDBC Driver - PostgreSQL ([guide](https://quarkus.io/guides/datasource)): Connect to the PostgreSQL database via JDBC
- JDBC Driver - H2 ([guide](https://quarkus.io/guides/datasource)): Connect to the H2 database via JDBC
- Hibernate Validator ([guide](https://quarkus.io/guides/validation)): Bean validation using Hibernate Validator and Jakarta Validation annotations
- SmallRye OpenAPI ([guide](https://quarkus.io/guides/openapi-swaggerui)): Generate OpenAPI schemas and serve Swagger UI for REST API documentation
- REST Jackson ([guide](https://quarkus.io/guides/rest#json-serialisation)): Jackson serialization support for Quarkus REST. This extension is not compatible with the quarkus-resteasy extension, or any of the extensions that depend on it
- Mapstruct ([guide](https://docs.quarkiverse.io/mapstruct/dev/)): MapStruct: Code generator for Java bean mappings using convention over configuration.
- Hibernate ORM with Panache ([guide](https://quarkus.io/guides/hibernate-orm-panache)): Simplified JPA/Hibernate data access layer with active record and repository patterns
