# F14 — Idempotent money movement

**Branch:** `feat/14-idempotent-movement` · **Depends on:** F13 · **Docker required:** yes

## Goal

Close the "call it twice, get two transfers" gap left open since F10/F11:
make every money-moving endpoint safe to retry.

## Scope

- `transfer/TransferOrchestrator` — `@Transactional(propagation =
  Propagation.NEVER)`, wraps `TransferService.execute`:
  - Attempts to insert the idempotency claim **in the same transaction** as
    the transfer itself.
  - On success, the transfer and its claim commit together.
  - On a unique-constraint violation (caught from the `PSQLException`,
    discriminated **by constraint name**), reads the existing record in a
    fresh transaction and returns the stored `response_status` /
    `response_body` verbatim.
  - On a business rejection (`BusinessRuleException` from F09), the whole
    transaction — transfer attempt and claim insert together — rolls back,
    so the key remains unused.
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
  over a two-phase claim/complete design: because Postgres blocks a second
  inserter on the unique index until the first transaction resolves,
  concurrency is handled by the database's own locking rather than
  application-level state. If the first transaction commits, the second
  gets a unique violation and replays. If the first rolls back (a business
  rejection), the second proceeds as if it were first. There is no
  `IN_PROGRESS` state, and therefore no "409, please retry" response to
  design around.
- **Must run with `Propagation.NEVER` at the orchestrator level, calling
  into `execute`'s own `@Transactional` boundary.** Postgres aborts an
  entire transaction on a constraint violation — there is no way to catch
  the violation and keep using the same transaction to read the
  now-visible conflicting row. The orchestrator therefore cannot itself be
  transactional; it must let `execute` run in its own transaction and
  handle the aftermath (success or violation) outside it.
- **Recovery must discriminate on the constraint name.** A
  `DataIntegrityViolationException` here could mean "the idempotency key
  was reused" (`idempotency_key_unique` — the expected, replay-worthy
  case) or "something else is broken" (e.g.
  `ledger_one_entry_per_account` from F08 — a real bug). Treating every
  unique violation as a replay would silently swallow the second class of
  failure.
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
docker info
./gradlew build
```

Integration tests:

- Replay with an identical body → same transfer id, same response, exactly
  one movement recorded (`LedgerInvariants` still holds).
- Same key, different amount → 422, fingerprint-mismatch detail.
- `N` concurrent requests with the identical key and body → exactly one
  `transfer` row, `N` responses all carrying that one transfer's id.
- **Key reusable after a failed attempt**: reject a transfer for
  insufficient funds using key `K`, deposit funds, retry the identical
  request with the same key `K` → 201. This is the test that proves the
  roll-back-the-claim design actually works end to end, not just in
  isolation.

## Review focus

- The constraint-name discrimination in the violation handler — a
  regression here (treating all violations as replays) would be a silent
  bug that only surfaces under a genuine data-integrity problem, i.e.
  exactly when you can least afford it to misbehave.
- Confirm there is no code path, anywhere, that deletes or expires an
  `idempotency_record`.
