# F15 — Concurrency tests

**Branch:** `feat/15-concurrency-tests` · **Depends on:** F14

## Goal

Test F09's write path under real concurrent load: no lost updates, and the
ledger stays balanced across many parallel transfers. This is a test-only
feature — no production code changes.

**Scope of what this proves, stated up front:** these tests run on H2, not
Postgres (see F00's testing-strategy section). They verify that
`TransferService.execute` serializes correctly under contention and that
money is neither created nor destroyed. They are **not** proof of F09's
specific claim about Postgres deadlock avoidance under the ascending-UUID
lock-ordering design — H2 resolves lock contention with a timeout rather
than Postgres's deadlock detector, so a genuine lock-ordering bug could
surface here as a slow test, a spurious failure, or nothing at all. That
property is verified by code review of the `order by a.id` query (F09) and
by exercising the app against real Postgres by hand.

## Scope

- `ConcurrentTransferIT`, driven through a virtual-thread executor
  (`Executors.newVirtualThreadPerTaskExecutor()`), three scenarios:
  1. **Single-account drain** — N parallel transfers all sourced from one
     account, asserting the final balance and ledger are exactly
     consistent with however many succeeded vs. were rejected for
     insufficient funds.
  2. **ABBA** — 16 threads transferring A→B concurrently with 16 threads
     transferring B→A. The single-account scenario never creates a lock
     *cycle*, since it only ever contends on one row; this one does.
  3. **Ring** — 8 accounts, 64 threads each transferring between a randomly
     chosen ordered pair, maximizing contention.
- Every scenario asserts `LedgerInvariants.assertAll` (from F09) at the
  end: ledger sums to zero, every balance matches the sum of its entries,
  no negative customer balance, no orphaned transfer without exactly one
  debit and one credit.

## Explicitly not in this feature

- No production code changes — if a scenario here fails, the fix belongs
  in F09 (or an earlier feature), not here.
- No claim of Postgres-specific deadlock-avoidance proof — see the scope
  note above.

## Design notes

Three traps that will silently produce a false-positive green suite if
missed:

- **The test class must not be `@Transactional`.** Spring's test
  transaction support wraps the whole test method in one transaction and
  rolls it back at the end. Under that annotation, the "parallel" workers
  either serialize behind the test's own connection/transaction or never
  actually commit against each other, and every invariant assertion passes
  vacuously — the test would look green while testing nothing.
  `AbstractIntegrationTest` (added in F09, not deferred to here as
  originally planned — a cross-test-pollution bug surfaced as soon as
  `LedgerInvariants` started running) already clears `ledger_entry`,
  `transfer`, and `account` via `DELETE FROM` in `@AfterEach` before this
  feature exists, so no additional cleanup is needed here — just don't
  undermine it with `@Transactional`.
- **Size the test datasource pool above the thread count.** With, say, 64
  worker threads and a default or small Hikari pool, most threads queue for
  a connection behind the ones currently holding row locks. If that queue
  wait exceeds `connection-timeout`, the test fails with
  `SQLTransientConnectionException` — which looks like a concurrency
  correctness bug but is actually a test-harness capacity problem. The
  test-scoped `application.yaml` needs a pool sized comfortably above the
  largest scenario's thread count.
- **`DB_CLOSE_DELAY=-1` (from F01) is mandatory here.** Parallel workers
  each take their own connection to the H2 in-memory database; without
  that setting, the database is torn down the moment any one connection
  closes, taking every other worker's session with it.

## Verification

```bash
./gradlew build
```

All three scenarios pass repeatably — run the suite a handful of times
locally, since a genuine race would be intermittent, not deterministic.

## Review focus

- That the test class is confirmed not `@Transactional` (the single most
  likely way this whole feature accidentally tests nothing).
- That the spec and any test comments are honest about scope: these tests
  back the "no lost updates" claim, not the Postgres-specific
  deadlock-avoidance claim.
