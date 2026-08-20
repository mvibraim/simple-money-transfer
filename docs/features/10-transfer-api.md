# F10 — Transfer API

**Branch:** `feat/10-transfer-api` · **Depends on:** F09 · **Docker required:** yes

## Goal

Expose the F09 write path over HTTP. Deliberately thin — this feature is
almost entirely wiring, because the hard part already landed in F09.

## Scope

- `transfer/TransferController` + request/response DTOs.
- `POST /api/v1/transfers` → 201 (`{sourceAccountId, targetAccountId,
  amount, currency, reference?}`).
- `GET /api/v1/transfers/{id}` → 200 / 404.

## Explicitly not in this feature

- No idempotency key requirement yet (F14) — calling this endpoint twice
  with the same body creates two transfers. That gap is closed in F14 and
  is acceptable to ship temporarily since this is all pre-production,
  feature-branch work.
- No deposit/withdrawal endpoints (F11).

## Design notes

- The controller does no business logic of its own — it maps the request
  DTO to `TransferService`'s command type, calls `execute`, and maps the
  domain exceptions F09 already defined through F03's existing advice.
  There should be nothing here to review except the mapping itself.

## Verification

```bash
docker info
./gradlew build
```

Integration tests drive the happy path and every F09 rejection path over
real HTTP, asserting the correct status code and `problem+json` shape for
each.

## Review focus

- Confirm the controller adds no logic beyond DTO mapping — any validation
  or business rule appearing here instead of in F09's service is a sign
  it's in the wrong layer.
