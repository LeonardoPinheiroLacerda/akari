# =========================================================
# Imagem de PRODUÇÃO (multi-stage, self-contained)
# =========================================================

# ---- Stage 1: build ----
# Imagem com JDK 25 completo + wrapper Maven para compilar a aplicação
FROM registry.access.redhat.com/ubi10/openjdk-25:1.24-14 AS build

# Root apenas no stage de build (descartado na imagem final)
USER 0

WORKDIR /build

# 'unzip' é exigido pelo Maven Wrapper: sem ele o wrapper baixa o .tar.gz
# em vez do .zip e a validação do distributionSha256Sum falha.
RUN microdnf install -y unzip
RUN microdnf clean all

# Copia o wrapper e o pom primeiro para aproveitar o cache de dependências:
# enquanto o pom.xml não muda, esta camada é reutilizada entre builds.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN chmod +x mvnw
RUN ./mvnw -B dependency:go-offline

# Copia o código-fonte e empacota o quarkus-app (fast-jar)
COPY --chown=185:0 src/ src/
RUN ./mvnw -B clean package -DskipTests

# ---- Stage 2: runtime ----
# Imagem enxuta (apenas JRE) para rodar a aplicação
FROM registry.access.redhat.com/ubi10/openjdk-25-runtime:1.24-14

WORKDIR /deployments

# Copia apenas os artefatos gerados, já com o dono correto (usuário 185)
COPY --from=build --chown=185:0 /build/target/quarkus-app/lib/ ./lib/
COPY --from=build --chown=185:0 /build/target/quarkus-app/*.jar ./
COPY --from=build --chown=185:0 /build/target/quarkus-app/app/ ./app/
COPY --from=build --chown=185:0 /build/target/quarkus-app/quarkus/ ./quarkus/

# Executa como usuário sem privilégios
USER 185

# Somente a porta HTTP (sem debug em produção)
EXPOSE 8080

# Roda o jar de produção diretamente
ENTRYPOINT ["java", "-Dquarkus.http.host=0.0.0.0", "-jar", "/deployments/quarkus-run.jar"]
