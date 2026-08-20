# F05 — Money representation

**Branch:** `feat/05-money-representation` · **Depends on:** F04 · **Docker required:** no

## Goal

Settle, once, how monetary amounts are validated and serialized — every
later feature that touches an amount depends on this being right, and it's
the cheapest feature in the whole roadmap to review carefully (pure
functions, no DB, no Docker).

## Scope

- A `Money`-adjacent support type / validator: ISO-4217 currency validation
  via `java.util.Currency`, `BigDecimal.setScale(4, RoundingMode.UNNECESSARY)`
  enforcement, and a check that the requested scale doesn't exceed the
  currency's `getDefaultFractionDigits()` (e.g. reject 3 decimal places on
  USD).
- Jackson configuration: amounts serialize as JSON **strings**
  (`@JsonFormat(shape = JsonFormat.Shape.STRING)`), not bare numbers;
  `spring.jackson.deserialization.fail-on-unknown-properties=true`;
  `use-big-decimal-for-floats=true`.
- Unit tests covering: valid amounts per currency, over-scale rejection,
  unknown currency codes, and unknown-field rejection on request bodies.

## Explicitly not in this feature

- No account or transfer code yet — this is validated in isolation with
  hand-built request records, not real DTOs (F06+ wire this in).

## Design notes

- **Postgres silently rounds** a `NUMERIC(19,4)` column on an over-scale
  insert rather than raising an error. The application-level scale check
  is the only thing standing between a client sending `10.12345` and that
  amount being silently truncated to `10.1235`. This is why the check
  lives here, at the boundary, rather than being left to the database.
- Amounts as JSON strings, not numbers: a bare JSON number can lose
  precision in a JavaScript client (or any language backed by IEEE 754
  doubles) before the server ever sees the value.
- `fail-on-unknown-properties=true` turns a typo like `{"ammount": "1.00"}`
  into a 400 instead of a silently-null `amount` field flowing into
  business logic.
- House rule recorded here for every feature downstream: **never call
  `BigDecimal.equals`** for monetary comparisons —
  `new BigDecimal("0.0000").equals(BigDecimal.ZERO)` is `false` because
  `equals` is scale-sensitive. Always use `compareTo`.

## Verification

```bash
./gradlew test -PexcludeTags=integration
```

## Review focus

- The scale-check boundary — confirm it actually runs before any value
  reaches a repository, not just in isolated unit tests.
- Every subsequent feature's monetary comparisons for accidental
  `BigDecimal.equals` usage (worth a standing note in `code-review`, not
  just this PR).
