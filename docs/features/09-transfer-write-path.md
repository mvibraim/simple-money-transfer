# F09 — The transfer write path

**Branch:** `feat/09-transfer-write-path` · **Depends on:** F08

## Goal

The single most important piece of the whole project: the code that
actually moves money between two accounts safely under concurrency. No HTTP
endpoint in this PR — reviewed purely as a service, which is what keeps this
diff small enough to read every line of carefully.

## Scope

- `transfer/TransferService.execute(TransferCommand)` — `@Transactional`
  (READ COMMITTED, the Postgres default):
  1. Validate before touching the DB: amount > 0, scale (F05), source ≠
     target.
  2. Lock both accounts in **one query, in deterministic order**:
     ```java
     @Lock(LockModeType.PESSIMISTIC_WRITE)
     @Query("select a from Account a where a.id in :ids order by a.id")
     List<Account> lockAllById(@Param("ids") Collection<UUID> ids);
     ```
  3. Assert invariants on the locked rows: both `ACTIVE`, currencies equal
     each other and the request, source balance ≥ amount.
  4. Mutate and record atomically: adjust both balances, insert the
     `transfer` row, insert exactly two `ledger_entry` rows carrying
     `balance_after`.
- Domain exceptions extending F03's `BusinessRuleException`:
  `InsufficientFundsException`, `CurrencyMismatchException`,
  `SelfTransferException`, `InactiveAccountException` — all map to 422.
- `LedgerInvariants.assertAll(JdbcClient)` test helper — checks
  `SUM(ledger_entry.amount) = 0`, every account's balance equals the sum of
  its own entries, no negative customer balance, and exactly one debit and
  one credit per transfer. Used from this feature's own tests and from
  every integration test's `@AfterEach` from here on.

## Explicitly not in this feature

- No HTTP endpoint (F10).
- No idempotency (F13/F14) — calling `execute` twice with the same
  arguments produces two transfers. That's acceptable at the service layer;
  idempotency is an orchestration concern layered on top.
- No system-account funding (F11) — `execute` works for any two accounts,
  but nothing seeds a SYSTEM account yet.

## Design notes

**Why pessimistic locking, acquired in ascending UUID order, over the
alternatives:**

- `SELECT ... FOR UPDATE` re-reads the latest committed row and blocks
  competing transactions, so READ COMMITTED is sufficient and no retry
  loop is needed — lost updates are impossible by construction.
- Optimistic locking (`@Version` + retry) is also correct, but converts
  contention on a hot account into a stream of failed transactions and
  retry storms rather than a bounded wait.
- A lock-free conditional `UPDATE ... SET balance = balance - :amt WHERE
  id = :id AND balance >= :amt` is efficient, but scatters the core
  business rule into a rowcount check and still needs the same statement
  ordering for deadlock safety — it doesn't actually avoid the ordering
  requirement, just hides it.

**The `order by a.id` is the entire deadlock-avoidance mechanism, and it's
why both accounts are locked in one query rather than two.** Two opposing
concurrent transfers (A→B and B→A) that each locked "source, then target"
would deadlock — transaction 1 holds A waiting for B, transaction 2 holds B
waiting for A. Because *every* transaction acquires locks in ascending
UUID order regardless of transfer direction, one of the two always
acquires both locks first and the other simply waits its turn. **This
guarantee only holds if every writer in the codebase uses this same
ordered-lock query — no other code path may lock account rows ad hoc.**

**`account_no_overdraft` (from F06) is defence in depth, not the
mechanism.** A violation of that CHECK constraint surfaces as an opaque
`DataIntegrityViolationException` at flush time, with no clean way to
report which business rule broke — by the time it fires, the ledger rows
for this transaction are already staged. The explicit balance check in
step 3 is what produces a clean 422; the constraint exists purely so that
a future bug in that check becomes a failed transaction instead of silent
money creation.

**The insufficient-funds check applies only to `CUSTOMER` sources.** A
`SYSTEM` account is where money enters and leaves the system (F11); its
balance is expected to run indefinitely negative as deposits accumulate,
exactly mirroring the DB's own `account_no_overdraft` exemption
(`account_type <> 'CUSTOMER' OR balance >= 0`). Missing this consistently
at the business-logic layer would have made every deposit fail with a
false "insufficient funds" once F11 tried to use `execute` for real —
caught here, before F11 exists, by writing the business-logic test for it
now rather than waiting to discover it against a real deposit flow.

## Verification

```bash
./gradlew test -PexcludeTags=integration   # Mockito: every rejection branch
./gradlew build                            # H2: happy path + atomicity
```

Split deliberately: **Mockito unit tests** mock `AccountRepository` and
drive every validation and rejection branch (insufficient funds, currency
mismatch, self-transfer, inactive account) without a Spring context or a
database — fast, and exhaustive on the business-rule logic. **H2
integration tests** cover the happy path end to end: both balances move,
a balanced ledger pair is posted, and each rejection path leaves balances
and the ledger completely unchanged. `LedgerInvariants.assertAll` passes
after every integration test.

H2 does support real `SELECT ... FOR UPDATE` row-level locking, so the
happy-path and atomicity tests genuinely exercise the lock-then-mutate
sequence — what they do *not* exercise is Postgres's specific deadlock
behavior under lock-order violation (see F00 and F15).

**Test fixtures fund test accounts through a real `DEPOSIT` transfer**
(from a throwaway `SYSTEM` account created in the test), not by setting
`balance` directly — a direct write with no matching ledger entry would
itself violate the invariant `LedgerInvariants` exists to check. This
surfaced a real gap earlier than planned: `LedgerInvariants.assertAll`
checks the ledger *globally*, so once it started running after F09's
tests, a leftover row from F06's `AccountRepositoryIT` (which sets a
`SYSTEM` account's balance directly via raw SQL to test the CHECK
constraint — correct for that test's own narrow purpose, unrelated to the
ledger) failed the check purely because all integration tests share one
H2 database for the whole run. Fixed by adding per-test cleanup to
`AbstractIntegrationTest` itself (`DELETE FROM` the domain tables,
children before parents, in `@AfterEach`) rather than deferring it to
F15 as originally planned — H2 refuses `TRUNCATE` on any table referenced
by a foreign key, even an empty one, so `DELETE` is used instead.

## Review focus

- The lock query's `order by a.id` — this single line is what the whole
  deadlock-avoidance argument rests on. Confirm no other repository method
  introduced elsewhere acquires an account lock without going through it.
- That every mutation (both balance updates, both ledger inserts) happens
  inside the same transaction as the lock acquisition, with nothing
  released and reacquired in between.
