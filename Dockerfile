FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY client-hub-common client-hub-common
COPY client-hub-domain client-hub-domain
COPY client-hub-infrastructure client-hub-infrastructure
COPY client-hub-application client-hub-application
COPY client-hub-web3 client-hub-web3
COPY client-hub-web client-hub-web

RUN chmod +x mvnw && ./mvnw clean package -DskipTests -B

FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system clienthub \
    && useradd --system --gid clienthub --home-dir /app clienthub \
    && mkdir -p /app /var/clienthub/uploads \
    && chown -R clienthub:clienthub /app /var/clienthub

WORKDIR /app

COPY --from=builder /workspace/client-hub-web/target/client-hub-web-*.jar /app/client-hub-web.jar

ENV SERVER_PORT=8080
EXPOSE 8080

USER clienthub

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD ["sh", "-c", "curl -fsS http://localhost:${SERVER_PORT}/actuator/health >/dev/null || exit 1"]

ENTRYPOINT ["java", "-jar", "/app/client-hub-web.jar"]
