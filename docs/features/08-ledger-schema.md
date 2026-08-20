# F08 — Ledger schema + immutability

**Branch:** `feat/08-ledger-schema` · **Depends on:** F07 · **Docker required:** yes

## Goal

Introduce the double-entry ledger tables and make history-rewriting
impossible at the database level, before any code writes to them. Schema
and its guarantees only — no write path yet (F09).

## Scope

- `src/main/resources/db/migration/V2__transfer_and_ledger.sql`:

  ```sql
  CREATE TABLE transfer (
    id                UUID PRIMARY KEY,
    source_account_id UUID NOT NULL REFERENCES account(id),
    target_account_id UUID NOT NULL REFERENCES account(id),
    amount            NUMERIC(19,4) NOT NULL,
    currency          CHAR(3) NOT NULL,
    kind              VARCHAR(16) NOT NULL,
    reference         VARCHAR(140),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT transfer_amount_positive   CHECK (amount > 0),
    CONSTRAINT transfer_distinct_accounts CHECK (source_account_id <> target_account_id),
    CONSTRAINT transfer_kind_valid        CHECK (kind IN ('TRANSFER','DEPOSIT','WITHDRAWAL'))
  );

  CREATE TABLE ledger_entry (
    id            BIGSERIAL PRIMARY KEY,
    transfer_id   UUID NOT NULL REFERENCES transfer(id),
    account_id    UUID NOT NULL REFERENCES account(id),
    direction     VARCHAR(6) NOT NULL,
    amount        NUMERIC(19,4) NOT NULL,
    currency      CHAR(3) NOT NULL,
    balance_after NUMERIC(19,4) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ledger_direction_valid CHECK (direction IN ('DEBIT','CREDIT')),
    CONSTRAINT ledger_sign_matches
      CHECK ((direction = 'DEBIT' AND amount < 0) OR (direction = 'CREDIT' AND amount > 0)),
    CONSTRAINT ledger_one_entry_per_account UNIQUE (transfer_id, account_id)
  );
  CREATE INDEX idx_ledger_account_history ON ledger_entry (account_id, id DESC);
  ```

- `src/main/resources/db/migration/V3__ledger_immutability.sql` — a
  `BEFORE UPDATE OR DELETE ON ledger_entry` trigger that `RAISE EXCEPTION`s.
- Entities: `transfer/Transfer`, `transfer/TransferKind`,
  `ledger/LedgerEntry`, `ledger/Direction`, plus their repositories.

## Explicitly not in this feature

- No service that actually writes a transfer or ledger entry (F09).
- No least-privilege DB role revoking `UPDATE`/`DELETE` — that's the
  privilege-level complement to this trigger, added in F16 once a second
  DB user is introduced.

## Design notes

- `ledger_entry.amount` is **signed** (debit negative, credit positive) so
  that `SUM(amount)` over the whole table is the single invariant that
  proves no money was created or destroyed — this is the one query used
  throughout the rest of the roadmap (F09's `LedgerInvariants` helper, and
  every integration test's `@AfterEach` from F09 onward).
- `ledger_one_entry_per_account UNIQUE (transfer_id, account_id)` enforces
  "exactly one entry per account per transfer" structurally — a bug that
  tried to double-post the same side of a transfer fails at the database
  rather than silently duplicating a movement.
- Immutability is enforced by a trigger, not by application-layer
  convention (e.g. "the repository just doesn't expose an update method").
  A trigger fails loudly regardless of what future code does; a missing
  method is trivially reintroduced by someone who doesn't know the rule.

## Verification

```bash
docker info
./gradlew build
```

Integration test inserts a ledger entry directly, then asserts both an
`UPDATE` and a `DELETE` against it raise from the trigger.

## Review focus

- The trigger function itself — confirm it fires on both `UPDATE` and
  `DELETE`, not just one.
- `ledger_sign_matches` — this is what makes "sum equals zero" mean
  something; without it a debit could be recorded positive and mask a real
  imbalance.
