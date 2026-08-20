# F15 — Concurrency proof

**Branch:** `feat/15-concurrency-proof` · **Depends on:** F14 · **Docker required:** yes

## Goal

Prove, under real concurrent load against real Postgres, that F09's locking
design does what its design notes claim: no lost updates, no deadlocks, and
the ledger stays balanced. This is a test-only feature — no production code
changes.

## Scope

- `ConcurrentTransferIT`, driven through a virtual-thread executor
  (`Executors.newVirtualThreadPerTaskExecutor()`), three scenarios:
  1. **Single-account drain** — N parallel transfers all sourced from one
     account, asserting the final balance and ledger are exactly
     consistent with however many succeeded vs. were rejected for
     insufficient funds.
  2. **ABBA** — 16 threads transferring A→B concurrently with 16 threads
     transferring B→A. This is the actual deadlock trigger: the
     single-account scenario never creates a lock *cycle*, since it only
     ever contends on one row.
  3. **Ring** — 8 accounts, 64 threads each transferring between a randomly
     chosen ordered pair, maximizing the chance of a longer lock cycle if
     the ordering guarantee in F09 were ever broken.
- Every scenario asserts `LedgerInvariants.assertAll` (from F09) at the
  end: ledger sums to zero, every balance matches the sum of its entries,
  no negative customer balance, no orphaned transfer without exactly one
  debit and one credit.

## Explicitly not in this feature

- No production code changes — if a scenario here fails, the fix belongs
  in F09 (or an earlier feature), not here.

## Design notes

Two traps that will silently produce a false-positive green suite if
missed:

- **The test class must not be `@Transactional`.** Spring's test
  transaction support wraps the whole test method in one transaction and
  rolls it back at the end. Under that annotation, the "parallel" workers
  either serialize behind the test's own connection/transaction or never
  actually commit against each other, and every invariant assertion passes
  vacuously — the test would look green while testing nothing. Cleanup is
  therefore manual: `TRUNCATE account, transfer, ledger_entry,
  idempotency_record RESTART IDENTITY CASCADE` in `@AfterEach` (never
  `flyway_schema_history`), or scope each test to its own freshly created
  accounts.
- **Size the test datasource pool above the thread count.** With, say, 64
  worker threads and a default or small Hikari pool, most threads queue for
  a connection behind the ones currently holding row locks. If that queue
  wait exceeds `connection-timeout`, the test fails with
  `SQLTransientConnectionException` / 503 — which looks like a concurrency
  correctness bug but is actually a test-harness capacity problem. The
  test-scoped `application.yaml` needs a pool sized comfortably above the
  largest scenario's thread count.
- Postgres is configured (via the F01 Testcontainers base, or explicit
  session settings in this feature) with a short `deadlock_timeout` so
  that if the ordering guarantee *were* broken, the test fails fast with a
  clear deadlock error rather than hanging until a long default timeout.

## Verification

```bash
docker info
./gradlew build
```

All three scenarios pass repeatably — run the suite a handful of times
locally, since a genuine race would be intermittent, not deterministic.

## Review focus

- That the test class is confirmed not `@Transactional` (the single most
  likely way this whole feature accidentally tests nothing).
- The ABBA and ring scenarios specifically — the single-account scenario
  alone would not have caught a broken lock-ordering guarantee.
