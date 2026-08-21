# F18 — Cursor-based ledger pagination

**Branch:** `feat/18-cursor-pagination` · **Depends on:** F12

## Goal

Replace F12's offset pagination (`page`/`size`, with `totalElements`/
`totalPages`) on `GET /api/v1/accounts/{id}/entries` with cursor
pagination, so the endpoint stays correct while the ledger is actively
being written to, and drops the per-request `COUNT(*)`.

## Scope

- `GET /api/v1/accounts/{id}/entries?cursor=<opaque>&limit=<1..100>` → 200.
  `cursor` is optional (omit to start at the newest entry); `limit`
  defaults to 20.
- Response: `{ entries, limit, nextCursor, hasMore }`. `nextCursor` is
  `null` once `hasMore` is `false`. `page`, `size`, `totalElements`,
  `totalPages` are gone - this is a breaking change to F12's contract, not
  an additive one.
- `CursorCodec` (`util`) - encodes/decodes an opaque, versioned (`v1:`)
  Base64 token wrapping a `ledger_entry.id`.
- `InvalidCursorException` (`exception`) - malformed cursor → 400.
- Bounds on `limit` (`@Min(1) @Max(100)`) now return 400 via Framework
  6.1's built-in handler-method validation, instead of the 500 the old
  `page`/`size` params produced for `size=0` (`PageRequest.of` threw an
  unmapped `IllegalArgumentException`).

## Explicitly not in this feature

- No composite/commit-ordered cursor - the ordering is still `id DESC`,
  unchanged from F12.
- No backward paging (`prevCursor`) - forward-only, matching F12's fixed
  newest-first contract.
- No filtering - same scope boundary F12 drew.

## Design notes

- **No new migration.** `idx_ledger_account_history (account_id, id DESC)`
  from F08 already serves `account_id = ? AND id < ? ORDER BY id DESC
  LIMIT ?` as an index scan - the same index the old offset query used.
- **`limit + 1` instead of a count query.** `LedgerService.getHistory`
  fetches one extra row; if it comes back, `hasMore` is true and the extra
  row is dropped before the cursor is derived from the last *kept* row.
  This gets an exact `hasMore` without a separate `COUNT(*)`, which is
  the query cost cursor pagination exists to avoid.
- **Opaque, versioned token.** `CursorCodec` wraps the id as `v1:<id>`,
  Base64-encoded. The version prefix means a future change to the cursor's
  key (e.g. a composite commit-timestamp+id, see below) can *reject* old
  tokens rather than silently misinterpret them.
- **`HandlerMethodValidationException`, not `@Validated`.** Framework 6.1+
  validates constrained `@RequestParam`s automatically inside
  `RequestMappingHandlerAdapter` and throws `HandlerMethodValidationException`
  on failure - no class-level `@Validated` needed. Adding `@Validated`
  anyway triggers a *second*, older mechanism
  (`MethodValidationPostProcessor`'s AOP `MethodValidationInterceptor`),
  which throws `jakarta.validation.ConstraintViolationException` instead
  and wins the race, bypassing `ApiExceptionHandler`'s new handler
  entirely and falling through to the generic 500. Confirmed by hitting
  this directly during implementation - `@Validated` was tried first and
  removed once the test suite showed the `ConstraintViolationException`
  stack trace.

### What this does and does not fix, relative to F12's accepted subtlety

F12 recorded that `ledger_entry.id` is assigned at insert time but
Postgres transactions can commit out of order, so `ORDER BY id` is
insertion order, not commit order.

Cursor pagination **narrows** this gap but does not **close** it:

- A row that becomes visible late, with an id *below* the client's current
  cursor, is still returned on the client's next request - descending
  cursor paging re-queries `id < cursor` every time, so a late-arriving
  low id is picked up exactly like any other row below the cursor. Offset
  paging did not have this property: a late-arriving row shifted every
  subsequent offset, causing skips or duplicates independent of where the
  row landed.
- The gap that remains: a row that commits *after* the client has already
  paged below its id is permanently invisible to that walk - the client's
  cursor is already past it. This is unchanged from F12 and not something
  cursor pagination can fix on its own.
- The mitigation is also unchanged from F12: a gap-free export must scope
  itself to rows older than the oldest in-flight transaction rather than
  relying on this endpoint's live cursor.

## Verification

```bash
./gradlew build
```

Integration test (`LedgerHistoryApiIT`): walking `nextCursor` until
`hasMore` is `false` visits every entry for an account exactly once;
`cursor`/`limit` out-of-contract values return 400; a page fetched by
cursor is unaffected by an entry inserted for the same account after the
cursor was issued (`pagingIsStableAcrossAConcurrentInsertBetweenRequests`).

Manual, against real Postgres (the whole performance argument rests on the
index being used, which H2 does not prove):

```sql
EXPLAIN ANALYZE SELECT * FROM ledger_entry
WHERE account_id = '<id>' AND id < 100 ORDER BY id DESC LIMIT 21;
-- expect Index Scan on idx_ledger_account_history, not Seq Scan + Sort
```

## Review focus

- That `LedgerService.getHistory`'s `limit + 1` fetch-and-trim is the only
  place `hasMore` is derived - no code path should reintroduce a
  `COUNT(*)`.
- That `CursorCodec.decode` rejects every malformed shape (bad Base64, no
  version prefix, unknown version, non-numeric id, negative id) via
  `InvalidCursorException`, never an unchecked exception that would leak
  to the generic 500 handler.
