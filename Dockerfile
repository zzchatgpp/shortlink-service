FROM maven:3.9.16-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY .mvn/settings.xml .mvn/settings.xml
RUN mvn -B -s .mvn/settings.xml dependency:go-offline
COPY src ./src
# Runs the JUnit/Mockito/H2 tests before packaging.
RUN mvn -B -s .mvn/settings.xml package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && apt-get clean \
    && groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --no-create-home app
COPY --from=build --chown=app:app /build/target/*.jar /app/app.jar
USER app
ENV PORT=8080
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS "http://localhost:${PORT:-8080}/health" || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
