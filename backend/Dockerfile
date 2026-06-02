# ── Stage 1: build ──────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Cache dependency layer separately from source
COPY pom.xml .
RUN mvn dependency:go-offline -B -q

COPY src ./src
RUN mvn package -DskipTests -B -q

# ── Stage 2: runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S portfolio && adduser -S portfolio -G portfolio

COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

RUN mkdir -p uploads && chown -R portfolio:portfolio /app

USER portfolio

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
