---
paths:
  - "src/test/**/*.java"
---

# Testing conventions

Mockito and an in-memory H2 database are this project's testing strategy — no Testcontainers, no Docker dependency in the test suite, no hand-rolled fakes.

- **Unit tests with no Spring context**: plain Mockito — `@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks`. Arrives transitively through the `-test` starters, no extra dependency needed.
- **Slice or `@SpringBootTest` tests that need to replace a bean**: `@MockitoBean` / `@MockitoSpyBean` from `spring-test`, not the deprecated `@MockBean` / `@SpyBean` from `spring-boot-test` (removed as of Boot 3.4). `@MockitoSpyBean` wraps the real bean instead of replacing it, so its lifecycle and dependencies still run — that's a behavioral difference from the old `@SpyBean`, not just a rename.
- **Integration tests that need a real datasource**: H2, in-memory, `testRuntimeOnly` — every run is hermetic, with no container startup cost and no Docker daemon required in CI. If the production engine's SQL dialect diverges from H2's defaults, point H2 at the matching compatibility mode (e.g. `jdbc:h2:mem:...;MODE=PostgreSQL`) rather than skipping context-loading tests over the mismatch.
