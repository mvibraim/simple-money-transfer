# Simple Money Transfers

A money-transfer API: create accounts, deposit and withdraw funds, and
transfer money between accounts, backed by a double-entry ledger and
Postgres. See `docs/features/00-index.md` for the full design roadmap and
the reasoning behind each part of it.

## Prerequisites

- Docker and Docker Compose
- Java 25 and Gradle are *not* required to run the packaged stack - only
  to build the application image or run the test suite yourself.

## Running the app

```bash
cp .env.example .env
# edit .env: set POSTGRES_PASSWORD, SPRING_FLYWAY_PASSWORD (same value),
# SPRING_DATASOURCE_PASSWORD (a different value, for the app's own
# restricted database role), and API_KEY (32+ characters).

./gradlew bootBuildImage          # builds simple-money-transfers:latest
docker compose -f compose.yaml -f compose.app.yaml up
```

The app listens on `localhost:8080`. Every endpoint except
`/actuator/health` requires an `X-API-Key` header matching the `API_KEY`
you set in `.env`.

## API documentation

The API is documented as OpenAPI 3.1, generated from the controller and
DTO annotations - no separate spec to keep in sync by hand:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Raw spec (JSON): `http://localhost:8080/v3/api-docs`

Both are reachable without an `X-API-Key`. A checked-in snapshot at
`src/test/resources/openapi.json` fails the build
(`OpenApiContractIT`) if the generated spec drifts from what's
committed; see that test's Javadoc for how to regenerate it after a
deliberate API change.

### Just the database

`docker compose up -d` (no `-f` flags) starts Postgres only - the loop for
running the app directly with `./gradlew bootRun` while developing, rather
than the fully packaged container.

## Smoke test

```bash
API_KEY=<your API_KEY from .env>

# Create two accounts
ALICE=$(curl -s -XPOST localhost:8080/api/v1/accounts -H "X-API-Key: $API_KEY" \
  -H 'Content-Type: application/json' -d '{"holderName":"Alice","currency":"USD"}' | python3 -c "import json,sys;print(json.load(sys.stdin)['id'])")
BOB=$(curl -s -XPOST localhost:8080/api/v1/accounts -H "X-API-Key: $API_KEY" \
  -H 'Content-Type: application/json' -d '{"holderName":"Bob","currency":"USD"}' | python3 -c "import json,sys;print(json.load(sys.stdin)['id'])")

# Fund Alice
curl -s -XPOST "localhost:8080/api/v1/accounts/$ALICE/deposits" -H "X-API-Key: $API_KEY" \
  -H 'Idempotency-Key: dep-1' -H 'Content-Type: application/json' -d '{"amount":"100.00"}'

# Transfer Alice -> Bob
curl -s -XPOST localhost:8080/api/v1/transfers -H "X-API-Key: $API_KEY" \
  -H 'Idempotency-Key: tr-1' -H 'Content-Type: application/json' \
  -d "{\"sourceAccountId\":\"$ALICE\",\"targetAccountId\":\"$BOB\",\"amount\":\"25.00\",\"currency\":\"USD\"}"

# Replay the identical request - same transfer id, no second movement
curl -s -XPOST localhost:8080/api/v1/transfers -H "X-API-Key: $API_KEY" \
  -H 'Idempotency-Key: tr-1' -H 'Content-Type: application/json' \
  -d "{\"sourceAccountId\":\"$ALICE\",\"targetAccountId\":\"$BOB\",\"amount\":\"25.00\",\"currency\":\"USD\"}"

# Check balances and ledger history (cursor-paginated, newest first)
curl -s "localhost:8080/api/v1/accounts/$ALICE/balance" -H "X-API-Key: $API_KEY"
curl -s "localhost:8080/api/v1/accounts/$ALICE/entries?limit=2" -H "X-API-Key: $API_KEY"
# Response includes "nextCursor"/"hasMore"; page again with
# ?limit=2&cursor=<nextCursor> until "hasMore" is false.
```

Expected failure modes:

| Request | Status |
|---|---|
| Amount exceeds the source balance | 422 |
| Source and target accounts have different currencies | 422 |
| Source and target account are the same | 422 |
| Unknown account id | 404 |
| Same `Idempotency-Key`, different request body | 422 |
| Missing `Idempotency-Key` on a transfer/deposit/withdrawal | 400 |
| Missing or wrong `X-API-Key` | 401 |
| Deposit into a currency with no seeded system account (e.g. KWD) | 422 |

## Running the test suite

```bash
./gradlew test -PexcludeTags=integration   # fast, no database at all
./gradlew build                            # full suite, H2 in-memory - still no Docker needed
```

Tests never touch Postgres - see `docs/features/00-index.md`'s testing
strategy section for why and what that trades away.

## Project layout

Each feature under `docs/features/` corresponds to one commit in the
project's history, in dependency order, each documenting its own scope,
design decisions, and what it deliberately left out. `docs/features/00-index.md`
is the place to start.
