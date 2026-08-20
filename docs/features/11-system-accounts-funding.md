# F11 — System accounts + funding

**Branch:** `feat/11-system-accounts-funding` · **Depends on:** F10 · **Docker required:** yes

## Goal

Give money a legitimate way to enter and leave the system, closing the gap
left by F07 (accounts always open at zero).

## Scope

- `src/main/resources/db/migration/V4__system_accounts.sql` — seeds one
  `SYSTEM`-type account per supported currency (reusing the `account` table
  from F06; `account_type = 'SYSTEM'` is the only value exempt from
  `account_no_overdraft`).
- `POST /api/v1/accounts/{id}/deposits` → 201
  (`{amount, currency, reference?}`).
- `POST /api/v1/accounts/{id}/withdrawals` → 201.
- Both reuse `TransferService.execute` from F09 with `kind = DEPOSIT` /
  `WITHDRAWAL` and the relevant currency's SYSTEM account as the other
  party — **no second write path**.

## Explicitly not in this feature

- No idempotency key requirement yet (F14) — same temporary gap as F10,
  closed in the same later feature.

## Design notes

- Routing deposits and withdrawals through the exact same `execute` method
  as transfers is the point of this feature, not an implementation detail:
  it means there is exactly one place in the codebase where a balance is
  ever mutated and a ledger entry is ever written, so every invariant F09
  established (locking, atomicity, the balanced-ledger guarantee) applies
  to funding for free.
- **Known scaling limit, accepted for now:** the per-currency SYSTEM
  account is a single row that every deposit and withdrawal in that
  currency locks, making it the first real throughput ceiling in the
  system. At this project's scale that's fine. If it ever isn't, the fix
  is sharding into several rows per currency (`SYSTEM/USD/0`,
  `SYSTEM/USD/1`, ...), chosen by hash, and summed for reporting — not
  redesigning the write path.

## Verification

```bash
docker info
./gradlew build
```

Integration test: deposit into a fresh account, confirm the balance moves
and `SUM(ledger_entry.amount)` across the whole table is still exactly
zero (the SYSTEM account's matching debit is what keeps it there).
Withdrawal below the account's balance → 422 via the same insufficient-
funds path as a regular transfer.

## Review focus

- That the SYSTEM account is genuinely the *only* row exempt from
  `account_no_overdraft`, and that no code path lets a `CUSTOMER` account
  reach it directly instead of through `execute`.
