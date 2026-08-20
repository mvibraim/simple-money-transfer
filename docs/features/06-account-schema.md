# F06 — Account schema + entity

**Branch:** `feat/06-account-schema` · **Depends on:** F05

## Goal

Introduce the first domain table and the first Flyway migration. No API
surface yet — this is schema and persistence only, kept separate from F07
so the DDL and its constraints can be reviewed without also reviewing HTTP
concerns.

## Scope

- `src/main/resources/db/migration/common/V1__account.sql` — portable DDL,
  runs on both Postgres and H2:

  ```sql
  CREATE TABLE account (
    id            UUID PRIMARY KEY,
    account_ref   VARCHAR(34)  NOT NULL UNIQUE,
    holder_name   VARCHAR(140) NOT NULL,
    account_type  VARCHAR(16)  NOT NULL DEFAULT 'CUSTOMER',
    currency      CHAR(3)      NOT NULL,
    balance       NUMERIC(19,4) NOT NULL DEFAULT 0,
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT account_currency_iso CHECK (REGEXP_LIKE(currency, '^[A-Z]{3}$')),
    CONSTRAINT account_type_valid   CHECK (account_type IN ('CUSTOMER','SYSTEM')),
    CONSTRAINT account_status_valid CHECK (status IN ('ACTIVE','FROZEN','CLOSED')),
    CONSTRAINT account_no_overdraft CHECK (account_type <> 'CUSTOMER' OR balance >= 0)
  );
  ```

- `src/main/resources/application.yaml`: add
  `spring.flyway.locations=classpath:db/migration/common,classpath:db/migration/{vendor}`
  (first migration lands, so this is where the `{vendor}` split actually
  starts mattering).
- `account/Account` JPA entity with `@Version` on the `version` column and
  `currency` mapped `@Column(updatable = false)`.
- `account/AccountType`, `account/AccountStatus` enums.
- `account/AccountRepository`.

## Explicitly not in this feature

- No HTTP endpoints (F07).
- No `SYSTEM` account rows yet — the migration only creates the table; the
  seed data for system accounts arrives in F11 alongside funding, once the
  transfer/ledger tables it depends on exist.
- No vendor-specific migration directory yet — `postgresql/` and `h2/`
  stay empty until F08 needs the trigger.

## Design notes

- **`REGEXP_LIKE` instead of `~`.** Postgres's `~` regex-match operator
  has no H2 equivalent; `regexp_like(string, pattern)` exists in both
  engines with identical semantics, so it's the portable choice for
  `common/`.
- `currency` is immutable after creation
  (`@Column(updatable = false)` on the entity). This is load-bearing for
  F09: the transfer write path's currency-match check is race-free only
  because currency can never change underneath a locked row.
- `account_no_overdraft` is written as defence in depth from the start,
  even though nothing writes to `balance` yet — it costs nothing to add now
  and means the invariant is never accidentally introduced after the fact.
- `version` (optimistic locking column) is kept even though F09 uses
  pessimistic locks for the transfer path — it still protects any future
  non-transfer edit to an account row (e.g. renaming a holder) from a lost
  update.

## Verification

```bash
./gradlew build
```

Integration test (H2) persists an account and asserts a direct
`UPDATE account SET balance = -1 WHERE account_type = 'CUSTOMER'` is
rejected by the database — the CHECK constraints are portable, so this
genuinely exercises the same rule that runs in production. Expect some
`ddl-auto: validate` friction on `CHAR(3)`/`UUID` column mapping between
Hibernate's dialect assumptions and H2; resolve with explicit
`columnDefinition` on the entity rather than by relaxing validation.

## Review focus

- Every CHECK constraint has a corresponding test that actually triggers
  it — a constraint nobody has ever violated in a test is a constraint
  nobody has verified exists.
- That the migration lives under `common/`, not a vendor-specific
  directory — nothing about this table is Postgres-specific.
