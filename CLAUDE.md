# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

This is an unmodified Spring Initializr scaffold — there is no domain code yet. `SimpleMoneyTransfersApplication` (the `@SpringBootApplication` entry point) and a `contextLoads` smoke test are the only two source files. Everything under `com.example.simple_money_transfers` is greenfield.

## Commands

```bash
./gradlew bootRun                  # run the app (devtools restart is active)
./gradlew build                    # compile + test + package
./gradlew test                     # run all tests
./gradlew test --tests 'SimpleMoneyTransfersApplicationTests'          # single test class
./gradlew test --tests '*Tests.contextLoads'                           # single test method
```

Test reports land at `build/reports/tests/test/index.html`; open it when a failure summary is too terse to act on.

Always invoke the wrapper (`./gradlew`), never a system-installed `gradle` — the wrapper is what pins the build to Gradle 9.5.1 and, via the toolchain, JDK 25.

## The build is red out of the box

`./gradlew test` currently fails, and it is *not* something you broke:

```
DataSourceProperties$DataSourceBeanCreationException
```

`spring-boot-starter-data-jpa` is on the classpath, but `application.properties` sets only `spring.application.name` and there is no embedded database dependency. Any `@SpringBootTest` will fail to start a context until a datasource exists. Before writing feature code, resolve this deliberately — don't work around it by weakening tests to avoid loading the context. Two current, non-beta ways to do that:

- **Fast, in-memory slices**: H2 as `testRuntimeOnly` (or `runtimeOnly` if it should also back `bootRun`).
- **Realistic engine, local dev and integration tests**: `org.springframework.boot:spring-boot-docker-compose` (`developmentOnly`) driving a `compose.yaml`, paired with `org.springframework.boot:spring-boot-testcontainers` (`testImplementation`) and a `@ServiceConnection`-annotated container in tests. Both auto-wire `spring.datasource.*` from the running container — don't hand-roll a JDBC URL alongside them.

## Spring Boot 4.1 / Spring Framework 7 conventions

The version matters here: **Boot 4.1.0 on Spring Framework 7.0**, Java 25 toolchain, Gradle 9.5.1. Boot 4 split the old monolithic starters and swapped several long-standing defaults, so names and patterns carried over from Boot 3 habits or older docs/answers will not resolve, or will silently pull in the wrong API:

- Web is `spring-boot-starter-webmvc`, **not** `spring-boot-starter-web`.
- There is no single `spring-boot-starter-test`. Test support is per-module and mirrors each production starter: `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-validation-test`. **Adding a production starter means adding its matching `-test` sibling**, otherwise that module's test-slice annotations and helpers are missing.
- JSON binding is **Jackson 3**, not 2 — the groupId moved from `com.fasterxml.jackson.*` to `tools.jackson.*` (databind, core, etc.), and `ObjectMapper` is now built via its own builder API. `jackson-annotations` is the one module that stayed on `com.fasterxml.jackson.annotation` for compatibility. Don't paste Jackson 2 imports or Maven/Gradle coordinates from older snippets.
- Nullability annotations are JSpecify (`org.jspecify.annotations.Nullable` / `NonNull`), not Spring's own `org.springframework.lang.Nullable`.
- For outbound HTTP, prefer declarative HTTP interface clients (`@HttpServiceClient` / `HttpServiceProxyFactory`) or `RestClient` over `RestTemplate`. For MVC slice tests, use `RestTestClient` — the new non-reactive default — instead of pulling in `WebTestClient`'s reactive dependencies.
- Error responses: return `ProblemDetail` (RFC 9457) from `@ExceptionHandler`s and set `spring.mvc.problemdetails.enabled=true` explicitly, rather than hand-rolling an error DTO.
- Structured JSON logging is built in: set `logging.structured.format.console=ecs|logstash|gelf` instead of hand-configuring a Logback JSON encoder.
- Virtual threads are opt-in, not default: `spring.threads.virtual.enabled=true` moves the embedded server and `@Async` executors onto them. Pairs naturally with Java 25's finalized Scoped Values (below) for request-scoped context instead of `ThreadLocal`.

JUnit 5 (Jupiter, on JUnit Platform 6), Mockito, AssertJ, and `spring-test` all arrive transitively through the `-test` starters — declare them explicitly only when you need a version the BOM doesn't supply.

Versions come from the `io.spring.dependency-management` BOM, so add dependencies without version numbers — and never override the BOM with a `-M*`/`-RC*`/`-SNAPSHOT` coordinate; wait for the GA release.

## Java 25 toolchain

The Gradle toolchain (`java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }`) pins the build to JDK 25, the current LTS (GA September 2025). Finalized features worth reaching for in application code:

- **Scoped Values** (JEP 506, final) — immutable, thread-confined context propagation. Prefer over `ThreadLocal` for request/tenant context, especially once virtual threads are enabled.
- **Flexible Constructor Bodies** (JEP 513, final) — validation and argument prep can now run before `super(...)`/`this(...)`, so fail-fast value objects (e.g. a money/amount type) don't need a static-factory workaround just to validate first.
- Compact Object Headers (JEP 519) and Generational Shenandoah (JEP 521) are JVM defaults finalized in 25 — no code change, just don't override them with `-XX` tuning flags copied from a Java 21-era guide.
- Module Import Declarations and Compact Source Files (JEPs 511/512, final) suit throwaway scripts, not this application's production or test sources.

Everything else on the JDK 25 feature list — Structured Concurrency, Primitive Types in Patterns, the PEM API, Stable Values, the Vector API — is still preview or incubator in 25. Don't take a dependency on a `--enable-preview` API in application code.

## Gradle 9 conventions

- Bump the wrapper deliberately (`./gradlew wrapper --gradle-version=<version>`) rather than letting an IDE drift onto whatever Gradle is installed locally; verify plugin compatibility before moving off 9.5.1.
- `validateDistributionUrl=true` is already set in `gradle-wrapper.properties` — keep it. It stops a tampered wrapper script from silently downloading the wrong distribution.
- Configuration cache is stable in Gradle 9 (no longer incubating) but still opt-in — add `org.gradle.configuration-cache=true` to `gradle.properties`, or pass `--configuration-cache`, once the build has enough tasks for it to pay off.
- Once dependencies grow past what the Spring BOM manages, move to a version catalog (`gradle/libs.versions.toml`) instead of scattering literal version strings through `build.gradle`.
- Same GA-only rule as the Spring BOM: don't add a Gradle plugin at a `-milestone-`/`-rc-` coordinate when a stable release covers the need.

`HELP.md` is generated by Initializr and gitignored — it is link boilerplate, not project documentation.
