# F13 — Idempotency store

**Branch:** `feat/13-idempotency-store` · **Depends on:** F12 · **Docker required:** yes

## Goal

Land the storage and fingerprinting building blocks for idempotency as an
isolated, no-behavior-change feature, so F14 (which wires them into the
actual request path) is reviewable as pure orchestration logic.

## Scope

- `src/main/resources/db/migration/V5__idempotency.sql`:

  ```sql
  CREATE TABLE idempotency_record (
    id              BIGSERIAL PRIMARY KEY,
    client_id       VARCHAR(64)  NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    fingerprint     CHAR(64)     NOT NULL,
    transfer_id     UUID NOT NULL REFERENCES transfer(id),
    response_status INT  NOT NULL,
    response_body   TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT idempotency_key_unique UNIQUE (client_id, idempotency_key)
  );
  ```

- `idempotency/IdempotencyRecord` entity + repository.
- `idempotency/RequestFingerprint` — computes a SHA-256 hex digest over the
  canonical form of `(kind, sourceAccountId, targetAccountId, amount,
  currency, reference)`.

## Explicitly not in this feature

- No wiring into `TransferService` or any controller — that's F14. Nothing
  in this feature changes observable API behavior.

## Design notes

- Scoped by `client_id` (`UNIQUE (client_id, idempotency_key)`), using the
  principal F04's `ApiKeyAuthFilter` sets — so one client's idempotency key
  can never collide with a different client's key of the same value.
- `transfer_id` is `NOT NULL` deliberately: this table only ever stores
  **successful** outcomes (see F14's design notes on why failed attempts
  don't get memoized). A record without a transfer would be a
  contradiction, so the schema doesn't allow it.
- Canonicalization for the fingerprint must be exhaustively unit tested —
  field order, number formatting, and null-handling all need to be stable,
  since a nondeterministic fingerprint would make the same logical request
  spuriously conflict with itself.

## Verification

```bash
docker info
./gradlew build
```

Unit tests for `RequestFingerprint`: same logical request → same digest
regardless of incidental differences (e.g. object construction order);
any field change → different digest.

## Review focus

- The fingerprint's canonical form — this is what F14's "same key,
  different body → 422" behavior depends on being both stable and
  sufficiently sensitive to real changes.
