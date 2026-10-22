# syntax=docker/dockerfile:1

FROM debian:trixie-slim AS build
RUN apt-get update \
    && apt-get install -y --no-install-recommends openjdk-21-jdk-headless curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*
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

FROM build AS java-runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends binutils \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /workspace/runtime-analysis
# Analyze every application module and dependency, including components discovered by Spring.
# Libraries are inputs only: also putting them on the classpath duplicates packages in jdeps.
RUN jar xf /workspace/app/build/libs/app.jar \
    && jdeps --ignore-missing-deps --multi-release 21 \
        --print-module-deps \
        BOOT-INF/classes BOOT-INF/lib/*.jar > /workspace/jdeps-modules.txt \
    && jlink --add-modules "$(cat /workspace/jdeps-modules.txt),jdk.crypto.ec,jdk.zipfs,jdk.unsupported" \
        --strip-debug --no-header-files --no-man-pages --compress=2 --output /opt/compact-java \
    && cp --remove-destination /etc/ssl/certs/java/cacerts /opt/compact-java/lib/security/cacerts \
    && /opt/compact-java/bin/java --list-modules > /workspace/linked-modules.txt

FROM debian:trixie-slim AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates libstdc++6 zlib1g \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --home-dir /app app

COPY --from=java-runtime /opt/compact-java /opt/java
COPY --from=java-runtime /workspace/jdeps-modules.txt /workspace/linked-modules.txt /opt/java/
ENV JAVA_HOME=/opt/java
ENV PATH="${JAVA_HOME}/bin:${PATH}"

WORKDIR /app
COPY --from=build --chown=app:app /workspace/app/build/libs/app.jar ./app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
