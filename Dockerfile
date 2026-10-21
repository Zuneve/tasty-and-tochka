# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY app/build.gradle.kts ./app/build.gradle.kts
COPY catalog/build.gradle.kts ./catalog/build.gradle.kts
COPY ordering/build.gradle.kts ./ordering/build.gradle.kts
COPY app/src ./app/src
COPY catalog/src ./catalog/src
COPY ordering/src ./ordering/src

RUN chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle ./gradlew :app:bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --home-dir /app app

WORKDIR /app
COPY --from=build --chown=app:app /workspace/app/build/libs/app.jar ./app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
