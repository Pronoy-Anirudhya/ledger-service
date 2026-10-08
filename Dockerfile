# syntax=docker/dockerfile:1

# ---- Build stage ----------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Wrapper and build scripts first: this layer (Gradle distribution + dependencies)
# is reused until one of these files changes.
COPY gradlew ./
COPY gradle/ gradle/
COPY settings.gradle.kts build.gradle.kts ./
RUN ./gradlew dependencies --no-daemon > /dev/null

COPY openapi/ openapi/
COPY src/ src/
RUN ./gradlew bootJar -x test --no-daemon \
    && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} /workspace/app.jar \;

# ---- Runtime stage --------------------------------------------------------
# glibc-based (Ubuntu) JRE: the TigerBeetle JNI client needs glibc, so no alpine.
FROM eclipse-temurin:25-jre

# The temurin JRE image does not ship curl; it is used by the compose healthcheck.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --no-create-home --shell /usr/sbin/nologin app

WORKDIR /app
COPY --from=build --chown=app:app /workspace/app.jar /app/app.jar

USER app
EXPOSE 8081

# P16: generational ZGC (the only ZGC mode in JDK 25), heap fixed at 75% of the
# container limit (Xms == Xmx), pre-touched; exit on OOM so the platform restarts it.
ENV JAVA_OPTS="-XX:+UseZGC -XX:InitialRAMPercentage=75 -XX:MaxRAMPercentage=75 -XX:+AlwaysPreTouch -XX:+ExitOnOutOfMemoryError --enable-native-access=ALL-UNNAMED"

# exec replaces the shell, so SIGTERM reaches the JVM (graceful shutdown).
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
