# F14 — Idempotent money movement

**Branch:** `feat/14-idempotent-movement` · **Depends on:** F13

## Goal

Close the "call it twice, get two transfers" gap left open since F10/F11:
make every money-moving endpoint safe to retry.

## Scope

- `transfer/IdempotentTransferAttempt` — a separate Spring bean holding
  the one `@Transactional` method that does the real work: run the
  transfer action, then insert the idempotency claim and `flush()`
  immediately, within that same transaction.
- `transfer/TransferOrchestrator` — the public entry point
  (`transfer`/`deposit`/`withdraw`), each `@Transactional(propagation =
  Propagation.NEVER)`:
  - Computes the request fingerprint, then calls
    `IdempotentTransferAttempt.run(...)`.
  - On success, returns 201 with the transfer, serialized once as the
    literal JSON string that gets stored for replay.
  - If that call throws `DataIntegrityViolationException` (see design
    notes for why nothing else could throw it there), the request is a
    duplicate: reads the existing record in a fresh transaction and
    returns the stored `response_status` / `response_body` verbatim, or
    422 if the fingerprint doesn't match.
  - On a business rejection (`BusinessRuleException` from F09), the whole
    transaction — transfer attempt and claim insert together — rolls
    back, so the key remains unused.
- `Idempotency-Key` header becomes **required** on `POST /transfers`,
  `/deposits`, and `/withdrawals` — its absence is a 400
  (`MissingRequestHeaderException`, newly mapped in `ApiExceptionHandler`).
- `IdempotencyConflictException extends BusinessRuleException` (422) —
  same key, different fingerprint.

## Explicitly not in this feature

- No TTL or cleanup job for idempotency records — deliberately, see design
  notes.
- No change to F09's `TransferService` itself — its three entry points
  (`execute`, `deposit`, `withdraw`) keep their existing signatures
  unchanged; the idempotency layer wraps them without modification.

## Design notes

- **The claim insert happens after the transfer action runs, not before —
  a deliberate departure from the original plan, forced by a genuine
  constraint conflict.** The original design wanted the claim inserted
  and flushed *before* any account is locked, so a known duplicate fails
  fast without doing the expensive work. But `idempotency_record.
  transfer_id` is `NOT NULL` (F13), and a transfer's id isn't known until
  `TransferService.execute`/`deposit`/`withdraw` actually creates it — and
  changing `TransferService` to accept a caller-supplied id was
  explicitly out of scope for this feature. Given that constraint, the
  claim is inserted immediately *after* the transfer action returns, but
  still inside the *same* transaction. Correctness is identical either
  way: if the claim insert's flush fails, the whole transaction — action
  included — rolls back, so a losing concurrent duplicate never leaves a
  partial transfer behind. The only real difference is efficiency, not
  safety: a losing duplicate now does the full locking/mutation work
  before being told to discard it, rather than failing before starting.
  `IdempotentTransferAttempt`'s Javadoc documents this trade-off.
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
- **The transactional claim-and-execute logic lives on a *different* bean
  than the orchestrator that calls it — not a private method on
  `TransferOrchestrator` itself.** Spring's transactional proxy only
  intercepts calls that arrive from a different bean; a `this.`-qualified
  call to a private (or even public) method on the same instance bypasses
  the proxy entirely; and a *private* method can never be proxied at all,
  regardless of caller. `IdempotentTransferAttempt` exists specifically so
  that calling it from `TransferOrchestrator` goes through a real proxy,
  and `execute`/`deposit`/`withdraw` (already their own proxied beans, on
  `TransferService`) correctly *join* that active transaction via default
  `REQUIRED` propagation rather than starting their own.
- **`TransferOrchestrator`'s entry methods run with `Propagation.NEVER`.**
  Both engines abort an entire transaction on a constraint violation —
  there is no way to catch the violation and keep using the same
  transaction to read the now-visible conflicting row. `NEVER` asserts
  this structurally: if a future refactor ever wrapped a controller in
  its own `@Transactional`, calling into the orchestrator would fail
  loudly instead of silently breaking the "separate transactions for
  attempt vs. replay" design.
- **Recovery is disambiguated by scoping, not by parsing constraint
  names.** An earlier version of this design caught
  `DataIntegrityViolationException` broadly and inspected the underlying
  `PSQLException`'s constraint name to tell "the idempotency key was
  reused" apart from "something else is broken." That's fragile
  (string-matching a vendor-specific constraint name) and, now that tests
  run on H2 rather than Postgres, not portable. The fix is structural: a
  transfer action's own work always creates a fresh `transfer_id`, so it
  can never collide with any of F08's constraints (all scoped to a
  specific `transfer_id`) — the *only* plausible source of
  `DataIntegrityViolationException` from `IdempotentTransferAttempt.run`
  is the idempotency-key unique index.
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
- **The stored and replayed response body is the literal JSON string**,
  produced once via the app's own `JsonMapper` bean and returned as
  `ResponseEntity<String>` with an explicit `application/json` content
  type — not re-serialized from a freshly-loaded `Transfer` on replay.
  "Verbatim" is taken literally: a replay is byte-for-byte identical to
  the original response, not merely semantically equivalent.

## Verification

```bash
./gradlew build
```

Integration tests (H2), in `transfer/IdempotencyIT`:

- Replay with an identical body → identical response body byte-for-byte,
  exactly one `transfer` row, correct final balances
  (`LedgerInvariants` still holds throughout).
- Same key, different amount → 422.
- 8 concurrent requests (virtual threads) with the identical key and body
  → all return 201, exactly one `transfer` row exists, the balance moved
  exactly once. H2's unique-index blocking is enough to prove this — no
  Postgres-specific behavior is relied on here.
- **Key reusable after a failed attempt**: reject a transfer for
  insufficient funds using key `K`, deposit funds, retry the identical
  request with the same key `K` → 201. This is the test that proves the
  roll-back-the-claim design actually works end to end, not just in
  isolation.

Also verified by hand against real Postgres 18: a replayed transfer
returns a byte-identical response with the same `id`/`createdAt`, the
balance moves only once, a missing `Idempotency-Key` returns 400 with a
clear detail, and `SUM(ledger_entry.amount)` stays exactly zero.

## Review focus

- That `IdempotentTransferAttempt` is a genuinely separate bean from
  `TransferOrchestrator` — merging them back into one class would
  silently break the transactional-proxy self-invocation guarantee this
  whole design rests on, with no compiler error to catch it.
- Confirm there is no code path, anywhere, that deletes or expires an
  `idempotency_record`.
