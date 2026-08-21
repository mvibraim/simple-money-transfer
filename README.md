# Simple Money Transfers

A money-transfer API: create accounts, deposit and withdraw funds, and
transfer money between accounts, backed by a double-entry ledger and
Postgres. See `docs/features/00-index.md` for the full design roadmap and
the reasoning behind each part of it.

## Prerequisites

- Docker and Docker Compose
- Java 25 and Gradle are *not* required to run the packaged stack - only
  to build the application image, run the test suite, or use the
  `bootRun` dev loop below.

## Running the app

```bash
cp .env.example .env
# edit .env - every value below is required, docker compose fails
# loudly at startup if any is missing:
#   POSTGRES_PASSWORD, SPRING_FLYWAY_PASSWORD (same value as
#     POSTGRES_PASSWORD - Flyway migrates as the Postgres owner)
#   SPRING_FLYWAY_USER (same value as POSTGRES_USER)
#   SPRING_DATASOURCE_PASSWORD (a different value, for the app's own
#     least-privilege database role, money_app)
#   API_CLIENT_ID (the principal name your API key authenticates as)
#   API_KEY (32+ characters)
# POSTGRES_DB, POSTGRES_USER, and POSTGRES_PORT have working defaults
# (money/money/5432) - only override POSTGRES_PORT if 5432 is already
# taken locally. Don't rename POSTGRES_DB: a migration hardcodes it.

docker compose up
```

The first run builds the app image from the `Dockerfile` (multi-stage:
Gradle/JDK 25 to compile, then a JRE-25-only runtime layer); later runs
reuse it unless you pass `--build` after changing source or
dependencies. The build includes a Java 25 AOT cache training run, so
the first build is slower than a plain compile - every subsequent
container start is faster for it.

The app listens on `localhost:8080`. Auth is header `X-API-Key`; the
public paths are exactly `/actuator/health`, `/v3/api-docs/**`,
`/swagger-ui/**`, and `/swagger-ui.html` - everything else, including
`/actuator/info` and `/actuator/metrics`, requires the header.

**`SPRING_DATASOURCE_PASSWORD` gets baked into the database on first
migration** - it's used to `CREATE ROLE money_app` with that password.
Changing it afterward doesn't re-run that migration, so the app can no
longer authenticate; fixing it means an `ALTER ROLE` by hand or dropping
the `pgdata` volume and starting over.

### Just the database

`docker compose up postgres -d` starts Postgres only - the loop for
running the app directly with `./gradlew bootRun` while developing,
rather than the fully packaged container. `bootRun` doesn't read `.env`
itself, so export it into the shell first:

```bash
docker compose up postgres -d
set -a; source .env; set +a
./gradlew bootRun
```

## API documentation

The API is documented as OpenAPI 3.1, generated from the controller and
DTO annotations - no separate spec to keep in sync by hand:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Raw spec (JSON): `http://localhost:8080/v3/api-docs`

Both are reachable without an `X-API-Key`. A checked-in snapshot at
`src/test/resources/openapi.json` fails the build
(`OpenApiContractIT`) if the generated spec drifts from what's
committed; regenerate it with `./gradlew test -PupdateOpenApiSnapshot`
and commit the diff.

## How it works

- **Double-entry ledger.** Every movement writes exactly two signed
  `ledger_entry` rows (debit negative, credit positive), so
  `SUM(amount) = 0` across the whole table is the money-conservation
  invariant. `account.balance` is a **materialized column**, not summed
  from the ledger at read time - the ledger is the audit trail and the
  correctness check, not the source of truth, and there's no
  reconciliation job.
- **Concurrency.** A transfer locks both accounts in one query, ordered
  ascending by id (`PESSIMISTIC_WRITE ... ORDER BY a.id`) - that ordering
  is the entire deadlock-avoidance mechanism. Under contention a request
  waits up to a bounded timeout, then gets a retryable 503 rather than
  hanging.
- **Funding.** Deposits and withdrawals reuse the exact same write path
  as transfers, against a per-currency `SYSTEM` account as the other
  party - there is exactly one place in the codebase that ever mutates a
  balance.

See `docs/features/08` and `09` (ledger + write path), `11` (funding),
and `16` (concurrency hardening) for the full reasoning.

## Idempotency

`Idempotency-Key` is required on `POST /transfers`, `/deposits`, and
`/withdrawals`; missing it is a 400.

- Scope is `(API_CLIENT_ID, Idempotency-Key)`, **global across
  endpoints** - reusing one key on a transfer and then a deposit collides
  as a 422, not two independent keys.
- Same key + identical body → the original 201 response, replayed
  **byte-for-byte**, same `Location` header.
- Same key + different body → 422.
- **A business rejection (insufficient funds, etc.) does not burn the
  key** - the claim rolls back with the rejected transaction, so retrying
  the identical request after fixing the underlying problem (e.g.
  depositing funds) succeeds.
- **Records are never reaped, by design** - a TTL would be a live
  double-spend window: delete a record, and a legitimate retry of that
  key executes a second, real transfer. The table grows without bound;
  that's the accepted trade. See `docs/features/13`/`14`.

## Security and deployment

**No per-account authorization.** The API key authenticates the
*client*, not an account owner - any valid key can move money out of
*any* account. This service must sit behind a gateway that does real
authorization, and must never be exposed directly to end users.

`/actuator/health` (no details, `show-details: never`) is the only
unauthenticated health signal and doubles as the container/compose
startup gate; there are no readiness/liveness probes.
`stop_grace_period: 40s` in `compose.yaml` is deliberately longer than
Boot's 30s graceful-shutdown drain, so a slow `docker compose down` is
expected, not a hang.

## Smoke test

```bash
API_KEY=<your API_KEY from .env>

# Create two accounts
ALICE=$(curl -s -XPOST localhost:8080/api/v1/accounts -H "X-API-Key: $API_KEY" \
  -H 'Content-Type: application/json' -d '{"holderName":"Alice","currency":"USD"}' | python3 -c "import json,sys;print(json.load(sys.stdin)['id'])")
BOB=$(curl -s -XPOST localhost:8080/api/v1/accounts -H "X-API-Key: $API_KEY" \
  -H 'Content-Type: application/json' -d '{"holderName":"Bob","currency":"USD"}' | python3 -c "import json,sys;print(json.load(sys.stdin)['id'])")

# Fetch an account
curl -s "localhost:8080/api/v1/accounts/$ALICE" -H "X-API-Key: $API_KEY"

# Fund Alice
curl -s -XPOST "localhost:8080/api/v1/accounts/$ALICE/deposits" -H "X-API-Key: $API_KEY" \
  -H 'Idempotency-Key: dep-1' -H 'Content-Type: application/json' -d '{"amount":"100.00"}'

# Transfer Alice -> Bob
TRANSFER=$(curl -s -XPOST localhost:8080/api/v1/transfers -H "X-API-Key: $API_KEY" \
  -H 'Idempotency-Key: tr-1' -H 'Content-Type: application/json' \
  -d "{\"sourceAccountId\":\"$ALICE\",\"targetAccountId\":\"$BOB\",\"amount\":\"25.00\",\"currency\":\"USD\"}" \
  | tee /dev/stderr | python3 -c "import json,sys;print(json.load(sys.stdin)['id'])")

# Replay the identical request - same transfer id, no second movement
curl -s -XPOST localhost:8080/api/v1/transfers -H "X-API-Key: $API_KEY" \
  -H 'Idempotency-Key: tr-1' -H 'Content-Type: application/json' \
  -d "{\"sourceAccountId\":\"$ALICE\",\"targetAccountId\":\"$BOB\",\"amount\":\"25.00\",\"currency\":\"USD\"}"

# Withdraw from Bob
curl -s -XPOST "localhost:8080/api/v1/accounts/$BOB/withdrawals" -H "X-API-Key: $API_KEY" \
  -H 'Idempotency-Key: wd-1' -H 'Content-Type: application/json' -d '{"amount":"10.00"}'

# Fetch the transfer by id
curl -s "localhost:8080/api/v1/transfers/$TRANSFER" -H "X-API-Key: $API_KEY"

# Check balances and ledger history (cursor-paginated, newest first)
curl -s "localhost:8080/api/v1/accounts/$ALICE/balance" -H "X-API-Key: $API_KEY"
curl -s "localhost:8080/api/v1/accounts/$ALICE/entries?limit=2" -H "X-API-Key: $API_KEY"
# Response includes "nextCursor"/"hasMore"; page again with
# ?limit=2&cursor=<nextCursor> until "hasMore" is false. limit defaults
# to 20 and is bounded 1-100; out of range or a malformed cursor is a 400.
```

All three money-moving endpoints also accept an optional `reference`
string (max 140 chars) for a client-supplied note. Amounts are JSON
**strings** in both requests and responses, at the currency's native
scale (e.g. 2 decimal places for USD, 0 for JPY) - never bare numbers,
to avoid floating-point precision loss.

### Error format

Every error is RFC 9457 `application/problem+json`. `type` is always
`about:blank` and `title` is just the HTTP reason phrase - there are no
per-error type URIs, so **clients discriminate on `status` + `detail`**,
not on `type`. A validation failure additionally carries an `errors`
array:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation failed",
  "instance": "/api/v1/accounts",
  "errors": ["holderName: must not be blank"]
}
```

Expected failure modes:

| Request | Status |
|---|---|
| Amount exceeds the source balance | 422 |
| Source and target accounts have different currencies | 422 |
| Source and target account are the same | 422 |
| Source or target account is FROZEN or CLOSED | 422 |
| Deposit/withdraw into a currency with no seeded system account (any currency but USD, EUR, GBP, JPY) | 422 |
| Same `Idempotency-Key`, different request body | 422 |
| Unknown account or transfer id | 404 |
| Missing `Idempotency-Key` on a transfer/deposit/withdrawal | 400 |
| Amount has more decimal places than the currency allows (e.g. `10.001` USD) | 400 |
| Currency code isn't valid ISO-4217 (e.g. `ZZZ`) - distinct from the funding-currency row above, which *is* valid ISO-4217 | 400 |
| Malformed request body, or an unrecognized JSON field | 400 |
| Malformed UUID in a path segment | 400 |
| Malformed `cursor`, or `limit` outside 1-100, on `GET .../entries` | 400 |
| Missing or wrong `X-API-Key` | 401 |
| Request can't be served right now - lock timeout, pool exhaustion, or a slow query (retry) | 503 |

## Running the test suite

```bash
./gradlew test -PexcludeTags=integration   # fast, no database at all
./gradlew build                            # full suite, H2 in-memory - still no Docker needed
```

Tests never touch Postgres - see `docs/features/00-index.md`'s testing
strategy section for why and what that trades away. Concretely: the
Postgres immutability trigger, the least-privilege role's `REVOKE`, the
syntax of every `postgresql/`-only migration, and the real
ascending-UUID lock-ordering behavior are **not exercised by
`./gradlew build`** - they're checked by hand against
`docker compose up -d && ./gradlew bootRun` after touching anything in
`db/migration/postgresql/`.

## Development

- `./gradlew format` reformats to Spring Java Format; `./gradlew
  checkFormat` verifies it and is wired into `check`, so a plain
  `./gradlew build` fails on unformatted code.
- CI runs four jobs on every PR to `main` (Build, Lint, Test, Docker
  image) - the only secret they need is `SONAR_TOKEN` for the Lint job's
  `./gradlew build sonar`, so a fork's PR will fail that one job without
  it.

## Project layout

Each feature under `docs/features/` corresponds to one commit in the
project's history, in dependency order, each documenting its own scope,
design decisions, and what it deliberately left out. `docs/features/00-index.md`
is the place to start.
