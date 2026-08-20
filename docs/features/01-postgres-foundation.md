# F01 — Postgres foundation

**Branch:** `feat/01-postgres-foundation` · **Depends on:** F00 · **Docker required:** yes (to run the full suite; unit tests stay Docker-free)

## Goal

Turn the build from red to green by giving the JPA starter an actual
database. Nothing domain-specific happens here — this is purely the
scaffolding that every later feature builds on.

## Scope

- `build.gradle`: add `runtimeOnly 'org.postgresql:postgresql'`,
  `implementation 'org.springframework.boot:spring-boot-starter-flyway'`,
  `runtimeOnly 'org.flywaydb:flyway-database-postgresql'`, and the matching
  test deps: `spring-boot-testcontainers`,
  `org.testcontainers:testcontainers-junit-jupiter`,
  `org.testcontainers:testcontainers-postgresql`,
  `spring-boot-starter-flyway-test`. All versions come from the Boot BOM —
  no version numbers declared.
- Replace `src/main/resources/application.properties` with
  `application.yaml`: `spring.jpa.hibernate.ddl-auto=validate`,
  `spring.jpa.open-in-view=false`, `spring.flyway.clean-disabled=true`.
- `src/test/java/.../support/AbstractPostgresIT.java` — `@SpringBootTest`
  base class with a `postgres:18-alpine` `@Container` wired via
  `@ServiceConnection`, tagged `@Tag("integration")`.
- Retarget `SimpleMoneyTransfersApplicationTests` to extend that base.
- Gradle `test` task reads a project property to exclude the `integration`
  tag, so `./gradlew test -PexcludeTags=integration` runs without Docker.

## Explicitly not in this feature

- No dev-convenience compose file (F02).
- No domain tables or entities (F06+).

## Design notes

- **Testcontainers 2.x renamed its coordinates.** The 1.x artifact names
  `postgresql` and `junit-jupiter` do not exist in the `testcontainers-bom`
  this project resolves (`2.0.5`); the correct names are
  `testcontainers-postgresql` and `testcontainers-junit-jupiter`. Verified
  directly against the cached BOM POM before writing this spec.
- `ddl-auto: validate` is chosen over `update` deliberately — schema is
  owned by Flyway migrations from F06 onward, and Hibernate only checks
  that the mapped entities agree with what's already there.
- Flyway is added now, before there's a single migration file, so the
  `flyway_schema_history` table and the validate-on-migrate behavior are
  exercised from the first commit rather than introduced as a surprise
  later.

## Verification

```bash
./gradlew test -PexcludeTags=integration   # green without Docker
docker info                                # must succeed first
./gradlew test                             # green with the Postgres container
```

## Review focus

- No version numbers anywhere in the new `build.gradle` lines — the BOM
  should be the only source of truth.
- `AbstractPostgresIT` is the only place a `postgres:18-alpine` container is
  declared; every later integration test extends it rather than declaring
  its own container.
