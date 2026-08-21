# syntax=docker/dockerfile:1

# Build stage: compiles, packages, and extracts the app into its layered
# form. Kept separate from the runtime stage so none of the JDK, Gradle
# wrapper, or source tree end up in the image that actually ships.
FROM eclipse-temurin:25-jdk@sha256:32861ec22e54af9597a3875c69001f57c0954648f5e3fcb6be601b4e35290ab5 AS build
WORKDIR /workspace

# Wrapper and build scripts first, source last: as long as neither
# changes, Docker reuses this layer across rebuilds triggered only by
# source edits. The cache mount persists the Gradle dependency cache
# across separate `docker build` invocations, on top of that.
COPY gradlew ./
COPY gradle gradle
COPY build.gradle settings.gradle gradle.properties ./
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon dependencies --configuration runtimeClasspath

COPY src src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon bootJar

# Renamed before extracting: this is what makes the resulting thin jar
# extracted/application/application.jar too, matching the ENTRYPOINT
# below. Safe to glob *.jar here specifically because this stage only
# ever runs bootJar, which emits exactly one jar (plus -plain.jar, which
# a plain `build` would also emit, but this Dockerfile never runs that).
RUN cp build/libs/*.jar application.jar \
 && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# Runtime stage: JRE only, non-root, the jar copied in as four separate
# layers (dependencies/spring-boot-loader/snapshot-dependencies/
# application) ordered least- to most-frequently-changed, so a source
# edit invalidates only the last, smallest layer.
FROM eclipse-temurin:25-jre@sha256:7c1c6297dc3a3ff947922f3ab14ecd326e29083b9edaa8dbff3b94fef1688311 AS runtime
WORKDIR /application

# curl: not present in eclipse-temurin by default, needed for the
# HEALTHCHECK below since /actuator/health needs no credentials
# (SecurityConfig) but does need an HTTP client inside the container.
RUN apt-get update \
 && apt-get install --no-install-recommends -y curl \
 && rm -rf /var/lib/apt/lists/*

RUN addgroup --system spring && adduser --system --ingroup spring spring

COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

# AOT cache training run (JEP 514, Boot 4.1's aot-cache support -
# CLAUDE.md's Java 25 toolchain section calls this "the highest-leverage
# deployment perf setting available on this stack"). spring.context.exit
# = onRefresh runs the context through bean init and JIT profiling, then
# exits before any application logic - but this app's context can't
# refresh without a reachable Postgres (Flyway migrates + ddl-auto:
# validate + no-default datasource placeholders), so refresh is faked
# out with training-only -D flags that are never used again after this
# layer. -XX:+UseCompactObjectHeaders must match between this run and
# the ENTRYPOINT below - it changes object layout, so a mismatch would
# silently invalidate the cache.
RUN java -XX:AOTCacheOutput=app.aot \
      -XX:+UseCompactObjectHeaders \
      -Dspring.context.exit=onRefresh \
      -Dspring.flyway.enabled=false \
      -Dspring.jpa.hibernate.ddl-auto=none \
      -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
      -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
      -Dspring.datasource.url=jdbc:postgresql://unused:5432/unused \
      -Dspring.datasource.username=unused \
      -Dspring.datasource.password=unused \
      -Dspring.datasource.hikari.initialization-fail-timeout=-1 \
      -Dapp.security.api-keys[0].id=training \
      -Dapp.security.api-keys[0].key=training-key-32-characters-minimum-xx \
      -jar application.jar \
 && chown spring:spring app.aot

USER spring:spring
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

# -XX:MaxRAMPercentage=75: the JVM default is 25%, which caps the heap
# far below what a container given, say, 512Mi actually has available.
# JAVA_TOOL_OPTIONS is deliberately left unset here (rather than baking
# flags into it) so it stays free as the standard operator override
# channel - anything set there at runtime augments these flags instead
# of silently replacing them.
ENTRYPOINT ["java", "-XX:AOTCache=app.aot", "-XX:+UseCompactObjectHeaders", "-XX:MaxRAMPercentage=75", "-jar", "application.jar"]
