# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

The domain is implemented: accounts, transfers (plus deposits/withdrawals), an append-only ledger, request idempotency, and money/currency validation, behind API-key auth with `ProblemDetail` error responses. Every class under `com.example.simple_money_transfers` is organized by layer per the Package structure section below — there is no per-feature package.

## Package structure

This project uses a **layer-based** package structure, not a feature-based one — organize by technical role, not by domain/feature. Every class under `com.example.simple_money_transfers` belongs in exactly one of these top-level packages:

- `repository` — Spring Data repository interfaces; the persistence layer contract only, no query logic beyond derived/`@Query` methods.
- `model` — entities and DTOs together, split into `model.entity` (`@Entity` classes) and `model.dto` (request/response DTOs). Don't scatter either under `controller` or `service`.
- `controller` — `@RestController` classes: request/response mapping and validation entry points only, no business logic.
- `service` — business logic and orchestration; the only layer allowed to coordinate across multiple repositories.
- `exception` — custom exception types plus `@ExceptionHandler` / `@ControllerAdvice` classes.
- `config` — `@Configuration` classes and bean definitions.
- `util` — stateless helper classes with no Spring-managed state.

When adding a new class, place it by what it *is* (controller, service, entity, ...), not by which feature it supports — there is no `transfer/`, `account/`, etc. feature package.

## Code style

Formatting is enforced, not a matter of taste: **Spring Java Format** (`io.spring.javaformat` Gradle plugin), the same formatter Spring Framework and Spring Boot use on themselves. Tabs, not spaces — this matches the tab-indented style Spring Initializr scaffolds with, so no reformat-away-from-the-scaffold tension.

- `./gradlew format` — reformat before committing.
- `./gradlew checkFormat` — wired into `check`, so a plain `./gradlew build` catches formatting violations too.
- Don't hand-format to match Spring Java Format's output — let the tool do it, and don't fight it with manual line breaks or alignment.

## AI collaboration conventions

- **Planning** — architecture and design decisions, entering plan mode, anything before code gets written: **Opus** at **xhigh** effort. This is a money-movement service; the design surface (ledger correctness, concurrency, idempotency) is worth the deeper pass before a line of code exists.
- **Everything else** — implementation, tests, docs, reviews, routine fixes: **Sonnet** at **high** effort. Switch back down once a plan is agreed and execution starts; don't stay on Opus/xhigh through routine work.

## Commands

```bash
./gradlew bootRun                  # run the app (devtools restart is active)
./gradlew build                    # compile + test + package
./gradlew test                     # run all tests
./gradlew test --tests 'SimpleMoneyTransfersApplicationTests'          # single test class
./gradlew test --tests '*Tests.contextLoads'                           # single test method
```

Test reports land at `build/reports/tests/test/index.html`; open it when a failure summary is too terse to act on.

Always invoke the wrapper (`./gradlew`), never a system-installed `gradle` — the wrapper is what pins the build to Gradle 9.7.1 and, via the toolchain, JDK 25.

`./gradlew build` also runs Jacoco (coverage report at `build/reports/jacoco/test/html/index.html`) and is what CI's Lint job pairs with `sonar` (`./gradlew build sonar`, needs `SONAR_TOKEN`) for SonarCloud analysis. `-PexcludeTags=<tag>[,<tag>...]` filters out JUnit-tagged tests from a `test` run. `bootBuildImage` publishes a fixed `simple-money-transfers:latest` tag (not the default group/name/version-derived one) so `compose.app.yaml` can reference it predictably.

## Local dev datasource

`bootRun` needs a real Postgres, not H2: `compose.yaml` starts one (`docker compose up`), and `compose.app.yaml` runs the packaged app image against it. Copy `.env.example` to `.env` and fill in the secrets `compose.yaml`/`compose.app.yaml` require before either compose file will start. Tests don't need any of this — see Testing conventions below.

Flyway migrations live under `src/main/resources/db/migration/common` (vendor-neutral) and `db/migration/postgresql` (Postgres-only), wired via `spring.flyway.locations: classpath:db/migration/common,classpath:db/migration/{vendor}`. Put a migration in `postgresql/` only if it genuinely needs Postgres-specific SQL — everything else belongs in `common/`, since the H2 test datasource (`MODE=PostgreSQL`) replays `common/` too.

## Spring Boot 4.1 / Spring Framework 7 conventions

The version matters here: **Boot 4.1.1 on Spring Framework 7.0**, Java 25 toolchain, Gradle 9.7.1. Boot 4 split the old monolithic starters and swapped several long-standing defaults, so names and patterns carried over from Boot 3 habits or older docs/answers will not resolve, or will silently pull in the wrong API:

- The platform baseline is **Jakarta EE 11**: Servlet 6.1, Bean Validation 3.1, Jakarta Persistence 3.2, WebSocket 2.2. Undertow doesn't support Servlet 6.1 and was dropped from Boot 4 entirely — don't add `spring-boot-starter-undertow`; the embedded container is Tomcat 11+ (the default alongside `spring-boot-starter-webmvc`) or Jetty 12.1+.
- Web is `spring-boot-starter-webmvc`, **not** `spring-boot-starter-web`.
- There is no single `spring-boot-starter-test`. Test support is per-module and mirrors each production starter — this project pulls in `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-validation-test`, `spring-boot-starter-flyway-test`, `spring-boot-starter-security-test`, and `spring-boot-starter-actuator-test` alongside their production counterparts. **Adding a production starter means adding its matching `-test` sibling**, otherwise that module's test-slice annotations and helpers are missing.
- JSON binding is **Jackson 3**, not 2 — the groupId moved from `com.fasterxml.jackson.*` to `tools.jackson.*` (databind, core, etc.), and `ObjectMapper` is now built via its own builder API. `jackson-annotations` is the one module that stayed on `com.fasterxml.jackson.annotation` for compatibility. Don't paste Jackson 2 imports or Maven/Gradle coordinates from older snippets.
- Nullability annotations are JSpecify (`org.jspecify.annotations.Nullable` / `NonNull`), not Spring's own `org.springframework.lang.Nullable`.
- For outbound HTTP, prefer declarative HTTP interface clients (`@HttpServiceClient` / `HttpServiceProxyFactory`) or `RestClient` over `RestTemplate`. For MVC slice tests, use `RestTestClient` — the new non-reactive default — instead of pulling in `WebTestClient`'s reactive dependencies.
- Error responses: return `ProblemDetail` (RFC 9457) from `@ExceptionHandler`s and set `spring.mvc.problemdetails.enabled=true` explicitly, rather than hand-rolling an error DTO.
- Structured JSON logging is built in (`logging.structured.format.console=ecs|logstash|gelf`) — not currently set in `application.yaml`, but reach for that property instead of hand-configuring a Logback JSON encoder if/when console logs need to be machine-parseable.
- Virtual threads are a required convention for this project, not an opt-in nicety: `spring.threads.virtual.enabled=true` is set in `application.yaml` — keep it there (not `application.properties`, which this project doesn't use) before writing blocking I/O (JDBC, outbound HTTP, file access) — it moves the embedded server and `@Async` executors onto virtual threads. On this JDK 25 toolchain the classic pinning hazard is already gone: JEP 491 (finalized in JDK 24) stopped `synchronized` blocks from pinning a virtual thread to its carrier, so the old advice to swap `synchronized` for `ReentrantLock` around blocking calls no longer applies here. Pairs naturally with Java 25's finalized Scoped Values (below) for request-scoped context instead of `ThreadLocal`.

**JUnit 6**, not JUnit 5, is what this project runs on: Boot 4.1.1's BOM manages Jupiter, Platform, and Vintage as a single unified `6.0.3` — JUnit 6 dropped the old split where Platform and Jupiter had different version numbers. It arrives transitively through the `-test` starters already pinned there; don't declare `org.junit.jupiter:junit-jupiter` at an explicit `5.x` coordinate from an older tutorial, and don't import `org.junit:junit-bom` yourself, since that would fight Boot's own dependency management. Mockito (`5.23.0`, same BOM) and AssertJ arrive the same way — declare versions explicitly only when you need something the BOM doesn't supply.

Versions come from the `io.spring.dependency-management` BOM, so add dependencies without version numbers — and never override the BOM with a `-M*`/`-RC*`/`-SNAPSHOT` coordinate; wait for the GA release.

## Testing conventions

Mockito and an in-memory H2 database are this project's testing strategy — no Testcontainers, no Docker dependency in the test suite, no hand-rolled fakes.

- **Unit tests with no Spring context**: plain Mockito — `@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks`. Arrives transitively through the `-test` starters, no extra dependency needed.
- **Slice or `@SpringBootTest` tests that need to replace a bean**: `@MockitoBean` / `@MockitoSpyBean` from `spring-test`, not the deprecated `@MockBean` / `@SpyBean` from `spring-boot-test` (removed as of Boot 3.4). `@MockitoSpyBean` wraps the real bean instead of replacing it, so its lifecycle and dependencies still run — that's a behavioral difference from the old `@SpyBean`, not just a rename.
- **Integration tests that need a real datasource**: H2, in-memory, `testRuntimeOnly` — every run is hermetic, with no container startup cost and no Docker daemon required in CI. If the production engine's SQL dialect diverges from H2's defaults, point H2 at the matching compatibility mode (e.g. `jdbc:h2:mem:...;MODE=PostgreSQL`) rather than skipping context-loading tests over the mismatch.

## Java 25 toolchain

The Gradle toolchain (`java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }`) pins the build to JDK 25, the current LTS (GA September 2025). Finalized features worth reaching for in application code:

- **Scoped Values** (JEP 506, final) — immutable, thread-confined context propagation. Prefer over `ThreadLocal` for request/tenant context, especially once virtual threads are enabled.
- **Flexible Constructor Bodies** (JEP 513, final) — validation and argument prep can now run before `super(...)`/`this(...)`, so fail-fast value objects (e.g. a money/amount type) don't need a static-factory workaround just to validate first.
- **Compact Object Headers** (JEP 519, final) — a product feature in 25, but **off by default**; enable explicitly with `-XX:+UseCompactObjectHeaders` (the `-XX:+UnlockExperimentalVMOptions` that JDK 24 required is gone). Shrinks object headers to 8 bytes — typically 10-20% less heap for live data on object-heavy workloads, so worth setting as a JVM flag on the deployed container. A draft JEP proposes flipping the default on in a future release, but 25 doesn't do it yet.
- G1 is the default collector, and JEP 523 (final in 25) made it the default even in memory-constrained containers, replacing the old Serial GC fallback — don't paste `-XX:+UseSerialGC` container-tuning advice from a pre-25 guide. Generational Shenandoah (JEP 521, final) is production-ready in 25 but only applies if Shenandoah is explicitly selected as the collector; it does not change the G1 default.
- **AOT cache** (JEP 483, refined by JEPs 514/515 in 25) — the flagship Boot 4 + Java 25 startup optimization, and the highest-leverage deployment perf setting available on this stack (~40%+ faster cold start in Spring's own benchmarks). Requires Boot 4.1.1+ (this project is on it) and runs against the *extracted* jar, not the fat jar:

  ```bash
  java -Djarmode=tools -jar app.jar extract --destination application
  cd application
  java -XX:AOTCacheOutput=app.aot -Dspring.context.exit=onRefresh -jar app.jar   # training run
  java -XX:AOTCache=app.aot -jar app.jar                                        # production start
  ```

  Any application or JDK change invalidates `app.aot`, so regenerate it as part of the image build rather than by hand — not yet wired into `bootBuildImage` here. Prefer this over classic CDS (`-XX:ArchiveClassesAtExit`), which is now the fallback for pre-25 JDKs only.
- Module Import Declarations and Compact Source Files (JEPs 511/512, final) suit throwaway scripts, not this application's production or test sources.

Structured Concurrency, Primitive Types in Patterns, the PEM API, Stable Values, and the Vector API are still preview or incubator in 25 — don't take a dependency on a `--enable-preview` API in application code for these.

## Gradle 9 conventions

- Bump the wrapper deliberately (`./gradlew wrapper --gradle-version=<version>`) rather than letting an IDE drift onto whatever Gradle is installed locally; verify plugin compatibility before moving off 9.7.1.
- `validateDistributionUrl=true` is already set in `gradle-wrapper.properties` — keep it. It stops a tampered wrapper script from silently downloading the wrong distribution.
- Configuration cache is stable in Gradle 9 (no longer incubating) and enabled here via `org.gradle.configuration-cache=true` in `gradle.properties`, alongside `org.gradle.caching=true` and `org.gradle.parallel=true`. The `org.sonarqube` plugin has supported the configuration cache since 7.2.0 (this project pins 7.4.0.8496), so `./gradlew build sonar` stays compatible.
- Once dependencies grow past what the Spring BOM manages, move to a version catalog (`gradle/libs.versions.toml`) instead of scattering literal version strings through `build.gradle`.
- Same GA-only rule as the Spring BOM: don't add a Gradle plugin at a `-milestone-`/`-rc-` coordinate when a stable release covers the need.

`HELP.md` is generated by Initializr and gitignored — it is link boilerplate, not project documentation.
