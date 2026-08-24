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

## Framework quick facts

These fail *silently* or pull the wrong API if guessed wrong, so they stay loaded in every session rather than in a path-scoped rule. Full rationale in `.claude/rules/spring-boot-4.md` and `.claude/rules/testing.md`, loaded automatically once Claude touches a matching file.

- Web starter is `spring-boot-starter-webmvc`, **not** `spring-boot-starter-web`.
- JSON binding is **Jackson 3** (`tools.jackson.*`), not Jackson 2 (`com.fasterxml.jackson.*`).
- Every production starter needs its matching `-test` sibling — there's no monolithic `spring-boot-starter-test`.
- **JUnit 6** arrives via Boot's BOM — never pin `org.junit.jupiter:junit-jupiter` at an explicit `5.x` coordinate, never import `junit-bom` yourself.
- No version numbers on BOM-managed dependencies, and never a `-M*`/`-RC*`/`-SNAPSHOT` coordinate; wait for GA.

## Code style

Formatting is enforced, not a matter of taste: **Spring Java Format** (`io.spring.javaformat` Gradle plugin), the same formatter Spring Framework and Spring Boot use on themselves. Tabs, not spaces — this matches the tab-indented style Spring Initializr scaffolds with, so no reformat-away-from-the-scaffold tension.

- `./gradlew format` — reformat before committing.
- `./gradlew checkFormat` — wired into `check`, so a plain `./gradlew build` catches formatting violations too. A `Stop` hook (`.claude/hooks/check-format.sh`) also runs this after any turn that touched a `.java` file.
- Don't hand-format to match Spring Java Format's output — let the tool do it, and don't fight it with manual line breaks or alignment.

## AI collaboration conventions

Money-movement design work — architecture and design decisions, concurrency, ledger correctness, idempotency, anything before code gets written — runs through the `money-design` subagent (`.claude/agents/money-design.md`, Opus at xhigh effort): the design surface on a money-movement service is worth the deeper pass before a line of code exists. Everything else — implementation, tests, docs, routine fixes — is plain Sonnet at high effort; don't stay on Opus/xhigh once a plan is agreed and execution starts. `.claude/agents/ledger-reviewer.md` (Sonnet, high effort) is the standing reviewer for changes touching transfers, the ledger, or concurrency.

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

`./gradlew build` also runs Jacoco (coverage report at `build/reports/jacoco/test/html/index.html`) and is what CI's Lint job pairs with `sonar` (`./gradlew build sonar`, needs `SONAR_TOKEN`) for SonarCloud analysis. `-PexcludeTags=<tag>[,<tag>...]` filters out JUnit-tagged tests from a `test` run. `bootBuildImage` publishes a fixed `simple-money-transfers:latest` tag (not the default group/name/version-derived one); the `Dockerfile`-based `docker compose up` build path (below) tags its image the same way, so either build method is interchangeable.

## Extended conventions

Everything below matters only when Claude is actually touching the relevant files, so it lives outside this always-loaded file:

- **Spring Boot 4.1 / Spring Framework 7 conventions** — `.claude/rules/spring-boot-4.md`, loads on `src/**/*.java` and `build.gradle`.
- **Java 25 toolchain** — `.claude/rules/java-25.md`, loads on `src/**/*.java`. The AOT cache training-run procedure is a `/aot-cache` skill instead — it's a one-shot procedure, not a fact to hold in context, and the `Dockerfile` is its source of truth.
- **Testing conventions** — `.claude/rules/testing.md`, loads on `src/test/**/*.java`.
- **Gradle 9 conventions** — `.claude/rules/gradle-build.md`, loads on `build.gradle`, `settings.gradle`, `gradle.properties`, `gradle/**`.
- **Local dev datasource** — `.claude/rules/database.md`, loads on Flyway migrations, `compose.yaml`, `.env.example`.

`HELP.md` is generated by Initializr and gitignored — it is link boilerplate, not project documentation.
