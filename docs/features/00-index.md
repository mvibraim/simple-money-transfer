# Feature Roadmap — Money Transfer API

Each feature below lands as its own branch, stacked on the previous one, and is
intended to be reviewed as a single small pull request. Every branch must
leave the build green (`./gradlew test -PexcludeTags=integration` at minimum;
`./gradlew build` once Docker is available).

| # | Branch | Scope | Docker needed |
|---|---|---|---|
| 00 | `docs/feature-specs` | This roadmap + all specs below | no |
| 01 | `feat/01-postgres-foundation` | Deps, `application.yaml`, Testcontainers harness → green build | yes |
| 02 | `feat/02-dev-database-compose` | `compose.yaml`, Boot compose support, `.env.example` | yes |
| 03 | `feat/03-error-contract` | RFC 9457 `ProblemDetail` advice + domain exception types | no |
| 04 | `feat/04-api-key-auth` | `ApiKeyAuthFilter`, `SecurityConfig`, actuator lockdown | no |
| 05 | `feat/05-money-representation` | Currency/scale rules, Jackson strictness | no |
| 06 | `feat/06-account-schema` | `V1__account.sql`, `Account` entity + repository | yes |
| 07 | `feat/07-account-api` | `POST/GET /accounts`, `GET /accounts/{id}/balance` | yes |
| 08 | `feat/08-ledger-schema` | `V2` transfer+ledger tables, `V3` immutability trigger | yes |
| 09 | `feat/09-transfer-write-path` | `TransferService.execute` — ordered locks, invariants | yes |
| 10 | `feat/10-transfer-api` | `POST /transfers`, `GET /transfers/{id}` | yes |
| 11 | `feat/11-system-accounts-funding` | `V4` system accounts, deposits + withdrawals | yes |
| 12 | `feat/12-ledger-history` | `GET /accounts/{id}/entries`, paginated | yes |
| 13 | `feat/13-idempotency-store` | `V5` table, `RequestFingerprint` | yes |
| 14 | `feat/14-idempotent-movement` | `TransferOrchestrator`, replay semantics, header wiring | yes |
| 15 | `feat/15-concurrency-proof` | Parallel/ABBA/ring integration tests | yes |
| 16 | `feat/16-operational-hardening` | Pool + DB timeouts, `ConstraintPresenceIT`, least-privilege role | yes |
| 17 | `feat/17-app-container-stack` | `compose.app.yaml`, image build, README | yes |

## Dependency order

Strictly linear. Each branch is stacked on the previous one — the Flyway
migrations are sequential (`V1`…`V5`) and later code depends on types
introduced earlier, so branches are not independently rebasable onto `main`
out of order.

## Status

All features are currently **planned, not yet implemented** (as of this
index's creation). Update the row below as each branch merges.

| Feature | Status |
|---|---|
| F00 | in progress |
| F01–F17 | planned |

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

## Verified environment facts

1. **Docker daemon may not be running locally.** Check with `docker info`
   before any Docker-tagged feature; F01 adds a Gradle tag toggle
   (`-PexcludeTags=integration`) so a Docker-free unit loop stays available.
2. **Boot 4.1 renamed things.** Boot 4 uses **Jackson 3** (`tools.jackson.*`
   packages), and **Testcontainers 2.x** coordinates are
   `testcontainers-postgresql` / `testcontainers-junit-jupiter` — the 1.x
   names `postgresql` / `junit-jupiter` don't exist on this BOM and won't
   resolve.
3. **Postgres 18's Docker image moved `PGDATA`** to a version-scoped
   `/var/lib/postgresql/18/docker`, with the declared `VOLUME` on the parent.
   The conventional `pgdata:/var/lib/postgresql/data` mount binds nothing —
   data goes to an anonymous volume and vanishes on `compose down` with no
   error. Mount `/var/lib/postgresql` instead.

## Out of scope — decisions, not oversights

- **No per-account authorization.** The API key authenticates the *client*,
  not an account owner, so any key holder can move money out of any account.
  This API must be deployed behind a gateway that does real authorization,
  never exposed directly to end users. Extension point: `owner_id` on
  `account` plus a principal→owner check before the lock.
- **No side effects inside the transfer write path.** The design is safe only
  because the unit of work is exactly one DB transaction. A webhook, email,
  or event publish inside it would be duplicated by retries. The correct
  extension is a transactional outbox.
- **Also deferred:** FX conversion for cross-currency transfers, rate
  limiting, reversals/refunds, scheduled or multi-leg transfers, KYC/AML
  screening.
