# F14 — Idempotent money movement

**Branch:** `feat/14-idempotent-movement` · **Depends on:** F13

## Goal

Close the "call it twice, get two transfers" gap left open since F10/F11:
make every money-moving endpoint safe to retry.

## Scope

- `transfer/TransferOrchestrator` — `@Transactional(propagation =
  Propagation.NEVER)`, wraps `TransferService.execute`:
  - Inserts the idempotency claim row and calls `flush()` **immediately**,
    inside a narrowly-scoped try/catch, before any account is locked or
    any ledger row is written.
  - If that insert succeeds, proceeds to call `execute` in its own
    transaction; on success, returns 201 with the transfer.
  - If that insert throws `DataIntegrityViolationException`, the request
    is a duplicate (see design notes for why nothing else could throw
    there): reads the existing record in a fresh transaction and returns
    the stored `response_status` / `response_body` verbatim, or 422 if the
    fingerprint doesn't match.
  - On a business rejection (`BusinessRuleException` from F09) inside
    `execute`, the whole transaction — transfer attempt and claim insert
    together — rolls back, so the key remains unused.
- `Idempotency-Key` header becomes **required** on `POST /transfers`,
  `/deposits`, and `/withdrawals` — its absence is a 400.

## Explicitly not in this feature

- No TTL or cleanup job for idempotency records — deliberately, see design
  notes.
- No change to F09's `TransferService` itself — the transactional
  boundary and replay logic live entirely in the new orchestrator layer.

## Design notes

- **The claim row lives in the same transaction as the transfer, not a
  separate "IN_PROGRESS" pre-claim.** This is a deliberate simplification
  over a two-phase claim/complete design: because both H2 and Postgres
  block a second inserter on the unique index until the first transaction
  resolves, concurrency is handled by the database's own locking rather
  than application-level state. If the first transaction commits, the
  second gets a unique violation and replays. If the first rolls back (a
  business rejection), the second proceeds as if it were first. There is
  no `IN_PROGRESS` state, and therefore no "409, please retry" response to
  design around.
- **Must run with `Propagation.NEVER` at the orchestrator level, calling
  into `execute`'s own `@Transactional` boundary.** Both engines abort an
  entire transaction on a constraint violation — there is no way to catch
  the violation and keep using the same transaction to read the
  now-visible conflicting row. The orchestrator therefore cannot itself be
  transactional; it must let `execute` run in its own transaction and
  handle the aftermath (success or violation) outside it.
- **Recovery is disambiguated by scoping, not by parsing constraint
  names.** An earlier version of this design caught
  `DataIntegrityViolationException` broadly around the whole write path
  and inspected the underlying `PSQLException`'s constraint name to tell
  "the idempotency key was reused" apart from "something else is broken."
  That's fragile (string-matching a vendor-specific constraint name) and,
  now that tests run on H2 rather than Postgres, not portable — H2's
  exception doesn't carry the same constraint-name detail in the same
  shape. The fix is structural instead: the claim insert is isolated to
  its own tight try/catch, executed *before* any other write in the
  request. A violation caught there can only be the idempotency-key unique
  constraint, because nothing else has been written yet to violate
  anything else.
- **A business rejection leaves the key reusable, on purpose.** If a
  transfer is rejected for insufficient funds, the transaction — and the
  claim row with it — rolls back. A client that then deposits funds and
  retries the *same* `Idempotency-Key` with the *same* body should
  succeed, not be stuck replaying a stale rejection forever. Only
  successful transfers are memoized; this is why `idempotency_record.
  transfer_id` is `NOT NULL` in F13's schema.
- **Records are never reaped, ever.** A time-based expiry on this table is
  a live double-spend window: delete a record, and a client's legitimate
  retry of that same key executes a second, real transfer. If storage
  pressure ever makes retention a real concern, the fix is to keep the
  `(client_id, idempotency_key) → transfer_id` mapping forever and drop
  only the cached `response_body`, never the key itself.

## Verification

```bash
./gradlew build
```

Integration tests (H2):

- Replay with an identical body → same transfer id, same response, exactly
  one movement recorded (`LedgerInvariants` still holds).
- Same key, different amount → 422, fingerprint-mismatch detail.
- `N` concurrent requests with the identical key and body → exactly one
  `transfer` row, `N` responses all carrying that one transfer's id. H2's
  unique-index blocking is enough to prove this — no Postgres-specific
  behavior is being relied on here.
- **Key reusable after a failed attempt**: reject a transfer for
  insufficient funds using key `K`, deposit funds, retry the identical
  request with the same key `K` → 201. This is the test that proves the
  roll-back-the-claim design actually works end to end, not just in
  isolation.

## Review focus

- The claim-insert scoping — confirm nothing else is written to the
  database between the claim insert and its `flush()`/catch, since that
  scoping is the entire basis for treating any violation caught there as
  "duplicate key," full stop.
- Confirm there is no code path, anywhere, that deletes or expires an
  `idempotency_record`.
