# F07 — Account API

**Branch:** `feat/07-account-api` · **Depends on:** F06

## Goal

Expose accounts over HTTP: create one, fetch one, check its balance. The
first real endpoints in the project, and the first place F03's error
contract and F04's auth get exercised end to end.

## Scope

- `account/AccountService`, `account/AccountController`.
- Request/response DTOs as Java records with Jakarta Validation.
- `POST /api/v1/accounts` → 201 (`{holderName, currency}` — always opens at
  a zero balance, see design note).
- `GET /api/v1/accounts/{id}` → 200 / 404.
- `GET /api/v1/accounts/{id}/balance` → 200, response includes an `asOf`
  timestamp.

## Explicitly not in this feature

- No way to fund an account yet (F11) — accounts open at zero and stay
  there until deposits exist.
- No transfer endpoint (F10).

## Design notes

- **Accounts always open at a zero balance — no `initialDeposit` field.**
  An opening balance written directly to the `balance` column has no
  matching ledger entry, which breaks `SUM(ledger_entry.amount) = 0` from
  the very first account created. Once that invariant is false, it can
  never again be trusted to catch a real bug — the entire point of
  double-entry accounting is undermined by a single unbalanced row. Funding
  is deliberately deferred to F11's deposit endpoint, which reuses the same
  audited write path as every other money movement.
- `GET .../balance` returns `asOf` rather than implying the value is live:
  a client that reads a balance and then acts on it has proven nothing
  about the balance at the time of the subsequent write (F09 re-validates
  under lock regardless) — making staleness explicit in the response
  discourages read-then-act races in client code.

## Verification

```bash
./gradlew build
```

Integration tests: create → fetch round-trip, validation failures (missing
holder name, invalid currency code) → 400, unknown id → 404, all requests
without `X-API-Key` → 401.

## Review focus

- Confirm there is genuinely no code path that can set a nonzero balance
  outside of F09's write path — this is the property F07 exists to
  preserve, not just describe.
