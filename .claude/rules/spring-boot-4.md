---
paths:
  - "src/**/*.java"
  - "build.gradle"
---

# Spring Boot 4.1 / Spring Framework 7 conventions

The version matters here: **Boot 4.1.1 on Spring Framework 7.0**, Java 25 toolchain, Gradle 9.7.1. Boot 4 split the old monolithic starters and swapped several long-standing defaults, so names and patterns carried over from Boot 3 habits or older docs/answers will not resolve, or will silently pull in the wrong API:

- The platform baseline is **Jakarta EE 11**: Servlet 6.1, Bean Validation 3.1, Jakarta Persistence 3.2, WebSocket 2.2. Undertow doesn't support Servlet 6.1 and was dropped from Boot 4 entirely — don't add `spring-boot-starter-undertow`; the embedded container is Tomcat 11+ (the default alongside `spring-boot-starter-webmvc`) or Jetty 12.1+.
- Web is `spring-boot-starter-webmvc`, **not** `spring-boot-starter-web`.
- There is no single `spring-boot-starter-test`. Test support is per-module and mirrors each production starter — this project pulls in `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-validation-test`, `spring-boot-starter-flyway-test`, `spring-boot-starter-security-test`, and `spring-boot-starter-actuator-test` alongside their production counterparts. **Adding a production starter means adding its matching `-test` sibling**, otherwise that module's test-slice annotations and helpers are missing.
- JSON binding is **Jackson 3**, not 2 — the groupId moved from `com.fasterxml.jackson.*` to `tools.jackson.*` (databind, core, etc.), and `ObjectMapper` is now built via its own builder API. `jackson-annotations` is the one module that stayed on `com.fasterxml.jackson.annotation` for compatibility. Don't paste Jackson 2 imports or Maven/Gradle coordinates from older snippets.
- Nullability annotations are JSpecify (`org.jspecify.annotations.Nullable` / `NonNull`), not Spring's own `org.springframework.lang.Nullable`.
- For outbound HTTP, prefer declarative HTTP interface clients (`@HttpServiceClient` / `HttpServiceProxyFactory`) or `RestClient` over `RestTemplate`. For MVC slice tests, use `RestTestClient` — the new non-reactive default — instead of pulling in `WebTestClient`'s reactive dependencies.
- Error responses: return `ProblemDetail` (RFC 9457) from `@ExceptionHandler`s and set `spring.mvc.problemdetails.enabled=true` explicitly, rather than hand-rolling an error DTO.
- Structured JSON logging is built in (`logging.structured.format.console=ecs|logstash|gelf`) — not currently set in `application.yaml`, but reach for that property instead of hand-configuring a Logback JSON encoder if/when console logs need to be machine-parseable.
- Virtual threads are a required convention for this project, not an opt-in nicety: `spring.threads.virtual.enabled=true` is set in `application.yaml` — keep it there (not `application.properties`, which this project doesn't use) before writing blocking I/O (JDBC, outbound HTTP, file access) — it moves the embedded server and `@Async` executors onto virtual threads. On this JDK 25 toolchain the classic pinning hazard is already gone: JEP 491 (finalized in JDK 24) stopped `synchronized` blocks from pinning a virtual thread to its carrier, so the old advice to swap `synchronized` for `ReentrantLock` around blocking calls no longer applies here. Pairs naturally with Java 25's finalized Scoped Values (see `.claude/rules/java-25.md`) for request-scoped context instead of `ThreadLocal`.

**JUnit 6**, not JUnit 5, is what this project runs on: Boot 4.1.1's BOM manages Jupiter, Platform, and Vintage as a single unified `6.0.3` — JUnit 6 dropped the old split where Platform and Jupiter had different version numbers. It arrives transitively through the `-test` starters already pinned there; don't declare `org.junit.jupiter:junit-jupiter` at an explicit `5.x` coordinate from an older tutorial, and don't import `org.junit:junit-bom` yourself, since that would fight Boot's own dependency management. Mockito (`5.23.0`, same BOM) and AssertJ arrive the same way — declare versions explicitly only when you need something the BOM doesn't supply.

Versions come from the `io.spring.dependency-management` BOM, so add dependencies without version numbers — and never override the BOM with a `-M*`/`-RC*`/`-SNAPSHOT` coordinate; wait for the GA release.
