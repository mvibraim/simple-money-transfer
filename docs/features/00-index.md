# Feature Roadmap — Money Transfer API

Each feature below lands as its own branch, stacked on the previous one, and
is intended to be reviewed as a single small pull request. Every branch
must leave the build green: `./gradlew test -PexcludeTags=integration` at
minimum, `./gradlew build` for the full suite. **No branch requires
Docker** — see "Testing strategy" below.

| # | Branch | Scope |
|---|---|---|
| 00 | `docs/feature-specs` | This roadmap + all specs below |
| 01 | `feat/01-postgres-foundation` | Deps, `application.yaml`, H2 test harness → green build |
| 02 | `feat/02-dev-database-compose` | `compose.yaml` for local Postgres, `.env.example` |
| 03 | `feat/03-error-contract` | RFC 9457 `ProblemDetail` advice + domain exception types |
| 04 | `feat/04-api-key-auth` | `ApiKeyAuthFilter`, `SecurityConfig`, actuator lockdown |
| 05 | `feat/05-money-representation` | Currency/scale rules, Jackson strictness |
| 06 | `feat/06-account-schema` | `common/V1__account.sql`, `Account` entity + repository |
| 07 | `feat/07-account-api` | `POST/GET /accounts`, `GET /accounts/{id}/balance` |
| 08 | `feat/08-ledger-schema` | `common/V2` transfer+ledger, `postgresql/V3` immutability trigger |
| 09 | `feat/09-transfer-write-path` | `TransferService.execute` — ordered locks, invariants |
| 10 | `feat/10-transfer-api` | `POST /transfers`, `GET /transfers/{id}` |
| 11 | `feat/11-system-accounts-funding` | `common/V4` system accounts, deposits + withdrawals |
| 12 | `feat/12-ledger-history` | `GET /accounts/{id}/entries`, paginated |
| 13 | `feat/13-idempotency-store` | `common/V5` table, `RequestFingerprint` |
| 14 | `feat/14-idempotent-movement` | `TransferOrchestrator`, replay semantics, header wiring |
| 15 | `feat/15-concurrency-tests` | Parallel/ABBA/ring tests on H2 (scoped — see caveat below) |
| 16 | `feat/16-operational-hardening` | Pool + DB timeouts, `ConstraintPresenceIT`, least-privilege role |
| 17 | `feat/17-app-container-stack` | `compose.app.yaml`, image build, README |
| 18 | `feat/18-cursor-pagination` | Cursor pagination on `GET /accounts/{id}/entries`, replacing F12's offset pagination |

## Dependency order

Strictly linear. Each branch is stacked on the previous — the Flyway
migrations are sequential (`V1`…`V5`) and later code depends on types
introduced earlier, so branches are not independently rebasable onto
`main` out of order.

## Status

| Feature | Status |
|---|---|
| F00 | in progress |
| F01–F17 | planned |

## Testing strategy

Tests **never touch Postgres**. Two tiers:

- **Unit tests — Mockito.** Service logic, validation, money/scale rules,
  fingerprinting: mocked repositories, no Spring context, no database.
- **Integration tests — H2 in-memory, PostgreSQL compatibility mode.**
  Spring context tests run against `jdbc:h2:mem:...;MODE=PostgreSQL`, with
  Flyway building the schema from the same migrations that ship to
  production.

This was chosen partly to route around a hard local blocker: **Testcontainers
cannot start containers on the reference development machine.** Docker
Engine 29.7.2 under Docker Desktop's LinuxKit VM fails every docker-java
container-create call with `OCI runtime create failed: namespace {"time"
""} does not exist`. Plain `docker run` and `docker compose` work fine —
only the docker-java client path fails. Ryuk-disable, `api.version`
pinning, and the Engine `features.time-namespaces=false` daemon flag were
all tried; none helped. So Testcontainers is out of the dependency set
entirely, and the whole test suite runs with no Docker at all.

Three consequences, recorded here rather than discovered later:

1. **The Postgres-only safety net is not exercised by tests.** The
   PL/pgSQL ledger-immutability trigger and the least-privilege `REVOKE`
   cannot exist in H2 (no PL/pgSQL; H2 triggers require a Java class).
   They stay in Postgres-only migrations as production defence in depth,
   and immutability is *additionally* enforced as an application-level
   invariant that H2 tests can verify.
2. **F15's concurrency tests prove something weaker than the write-path
   design claims.** H2's locking is not Postgres's — H2 resolves
   contention with a lock timeout rather than Postgres's deadlock
   detection. The tests verify the service serializes correctly and the
   ledger stays balanced; they are explicitly *not* proof of the
   ascending-UUID lock-ordering design. That property rests on code review
   plus running against real Postgres by hand.
3. **The Postgres-only migrations have no automated coverage.** A syntax
   error in a `postgresql/`-only migration file surfaces only when the app
   actually runs against Postgres. Mitigation: F02's compose stack is the
   manual check, and it must be re-run after any migration change.

## Portable-DDL rules

Every migration under `db/migration/common/` must stay inside a subset
both Postgres 18 and H2 2.4 (`MODE=PostgreSQL`) accept, verified against
both engines' documentation:

| Instead of | Use | Why |
|---|---|---|
| `BIGSERIAL` | `BIGINT GENERATED BY DEFAULT AS IDENTITY` | SQL-standard; H2 2.x dropped `AUTO_INCREMENT` in favour of it, Postgres has had it since 10 |
| `currency ~ '^[A-Z]{3}$'` | `REGEXP_LIKE(currency, '^[A-Z]{3}$')` | `~` is Postgres-only; `regexp_like` exists in Postgres 15+ and H2 2.x with the same meaning |
| `TIMESTAMPTZ` | `TIMESTAMP WITH TIME ZONE` | `TIMESTAMPTZ` is a Postgres-only alias |
| `DEFAULT now()` | `DEFAULT CURRENT_TIMESTAMP` | standard in both |
| `pg_constraint` / `pg_indexes` | `INFORMATION_SCHEMA.TABLE_CONSTRAINTS` | SQL-standard, present in both |

`UUID`, `NUMERIC(19,4)`, `CHECK`, `UNIQUE`, `REFERENCES` are portable
as-is. Migrations are split by Spring's `{vendor}` placeholder (resolves
via `DatabaseDriver.getId()` — confirmed `postgresql` and `h2`):

```yaml
spring.flyway.locations: classpath:db/migration/common,classpath:db/migration/{vendor}
```

`common/` holds everything portable. `postgresql/` holds the PL/pgSQL
trigger and the least-privilege `REVOKE`. Only one vendor directory is
ever on the path per run, so a version number used in `postgresql/` need
not exist in `h2/`.

## Locked design decisions

| Area | Decision |
|---|---|
| Auth | Static API key in `X-API-Key` via Spring Security |
| Ledger | Double-entry: immutable ledger entries (debit + credit per movement, summing to zero) + materialized balance |
| Currency | Multi-currency accounts (ISO-4217); same-currency transfers only |
| Schema | Flyway versioned SQL; Hibernate `ddl-auto: validate` |
| Money | `NUMERIC(19,4)` → `BigDecimal`, serialized as JSON strings |
| Concurrency | Pessimistic row locks acquired in ascending UUID order |
| Funding | Deposits/withdrawals booked against a per-currency SYSTEM account |
| Prod DB | Postgres 18 via docker-compose |
| Test DB | H2 in-memory, `MODE=PostgreSQL` |

## Verified environment facts

1. **Boot 4.1 renamed things.** Read from the real
   `spring-boot-dependencies:4.1.0` POM: Boot 4 uses **Jackson 3**
   (`tools.jackson.*` packages); starters are `spring-boot-starter-webmvc`,
   `-security`, `-flyway`, `-actuator`, each with a `-test` sibling. H2
   2.4.240 and Mockito 5.23.0 come from the BOM; Mockito arrives
   transitively via the `-test` starters, so it needs no explicit
   declaration.
2. **Postgres 18's Docker image moved `PGDATA`** to a version-scoped
   `/var/lib/postgresql/18/docker`, with the declared `VOLUME` on the
   parent. The conventional `pgdata:/var/lib/postgresql/data` mount binds
   nothing — data goes to an anonymous volume and vanishes on `compose
   down` with no error. Mount `/var/lib/postgresql` instead.

## Out of scope — decisions, not oversights

- **No per-account authorization.** The API key authenticates the
  *client*, not an account owner, so any key holder can move money out of
  any account. This API must be deployed behind a gateway that does real
  authorization, never exposed directly to end users. Extension point:
  `owner_id` on `account` plus a principal→owner check before the lock.
- **No side effects inside the transfer write path.** The design is safe
  only because the unit of work is exactly one DB transaction. A webhook,
  email, or event publish inside it would be duplicated by retries. The
  correct extension is a transactional outbox.
- **Also deferred:** FX conversion for cross-currency transfers, rate
  limiting, reversals/refunds, scheduled or multi-leg transfers, KYC/AML
  screening.
- **Not verified by any automated test:** the Postgres immutability
  trigger, the least-privilege `REVOKE`, the syntax of every
  `postgresql/`-only migration, and the true lock-ordering behaviour under
  Postgres. All four are production-only guarantees resting on review plus
  the manual `docker compose up && ./gradlew bootRun` check.
