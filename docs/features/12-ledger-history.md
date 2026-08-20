# F12 — Ledger history

**Branch:** `feat/12-ledger-history` · **Depends on:** F11

## Goal

Let a caller see the audit trail behind an account's balance — the whole
point of building a double-entry ledger instead of a bare balance column.

## Scope

- `GET /api/v1/accounts/{id}/entries` → 200, paginated (`page`, `size`
  query params), ordered by `id DESC` (using the index from F08).
- `LedgerEntryResponse` DTO — direction, signed amount, currency,
  `balanceAfter`, `createdAt`, and the originating `transferId`.

## Explicitly not in this feature

- No filtering by date range or transfer kind — pagination only, kept
  minimal since nothing in the roadmap currently needs more.

## Design notes

- **Known, accepted subtlety:** `ledger_entry.id` is an identity column,
  assigned at insert time, but Postgres transactions can commit in a
  different order than they inserted — id 100 can become visible after id
  101 if transaction 100 is slower to commit. So `ORDER BY id` is
  *insertion* order, not *commit* order. For interactive paging this is
  invisible (a human refreshing a page won't notice). It would matter for
  a concurrent financial export that needs a commit-ordered, gap-free
  cursor — out of scope here, and if it's ever needed the fix is scoping
  the export to rows older than the oldest in-flight transaction rather
  than changing this endpoint.

## Verification

```bash
./gradlew build
```

Integration test: after several transfers touching one account, the
returned page reflects exactly the balances and directions recorded by
F09, and paging through all pages yields every entry exactly once.

## Review focus

- That pagination is stable under concurrent writes to the *same* account
  (no duplicate or skipped rows across a page boundary during a single
  request) — the known commit-ordering subtlety above is a *different*
  and accepted concern, not this one.
