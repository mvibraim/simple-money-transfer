# F16 — Operational hardening

**Branch:** `feat/16-operational-hardening` · **Depends on:** F15

## Goal

Close the operational gaps that don't show up in correctness tests but will
show up in production: pool exhaustion under lock contention, a
silently-droppable constraint, and an application role with more database
privilege than it needs.

## Scope

- Explicit Hikari pool sizing and a short `connection-timeout` in
  `application.yaml`.
- Postgres session settings (via `compose.yaml` from F02, and/or connection
  init): `lock_timeout`, `statement_timeout`,
  `idle_in_transaction_session_timeout`, `deadlock_timeout=200ms`,
  `log_lock_waits=on`.
- Map pool-exhaustion and lock-timeout exceptions to **503** in F03's
  advice.
- A second database role, `money_app`, for the application (distinct from
  the Flyway migration owner), created in
  `db/migration/postgresql/V6__app_role.sql` (Postgres-only, so untestable
  on H2 — verified by hand instead, see Verification below). It is granted
  `SELECT, INSERT, UPDATE` on `account` and only `SELECT, INSERT` on
  `transfer`, `ledger_entry`, and `idempotency_record` — extended beyond
  the original `ledger_entry`-only scope to the other two append-only
  audit tables, for the same reason (F08, F13). `spring.flyway.user`/
  `spring.flyway.password` are configured independently of
  `spring.datasource.*`, so Flyway keeps owner (DDL) privileges while the
  running application connects as `money_app`.
- `ConstraintPresenceIT` — queries
  `INFORMATION_SCHEMA.TABLE_CONSTRAINTS` (SQL-standard, present in both
  engines — not `pg_constraint`/`pg_indexes`, which are Postgres system
  catalogs H2 doesn't have) and asserts every named constraint from
  F06/F08/F13 still exists. Runs on H2, so it's part of the normal
  Docker-free suite.

## Explicitly not in this feature

- No new business behavior — this feature only removes privilege and adds
  timeouts/verification around what F06–F15 already built.

## Design notes

- **These settings are required together, not individually.** Pessimistic
  locking (F09) turns one slow or stuck transaction into held row locks;
  without `lock_timeout`/`statement_timeout` a slow transaction can block
  others indefinitely, and without a bounded pool + short
  `connection-timeout`, those blocked transactions exhaust the connection
  pool and start failing requests on *unrelated* endpoints that have
  nothing to do with transfers. Any one of these controls missing leaves
  that failure mode open — this is arguably the most likely way this
  service actually goes down in production, more likely than a
  correctness bug.
- **`ddl-auto: validate` (from F01) does not validate the constraints this
  whole project's correctness depends on.** Hibernate's schema validation
  checks tables, columns, and types — it does **not** check CHECK
  constraints, foreign keys, unique indexes, or triggers. A future
  migration that accidentally drops `account_no_overdraft` or
  `idempotency_key_unique` would start the application cleanly, with no
  signal that a real invariant is gone. `ConstraintPresenceIT` is the only
  thing in the whole test suite that would catch that — and because it
  reads `INFORMATION_SCHEMA` rather than a Postgres system catalog, it
  actually runs as part of the everyday H2 suite instead of being another
  Postgres-only, manually-checked guarantee.
- The application DB role losing `UPDATE`/`DELETE` on `ledger_entry` is the
  privilege-level enforcement of F08's immutability trigger: the trigger
  stops *this codebase's* ORM from rewriting history; the revoked grant
  stops *any* code running as the application — including a future
  ad-hoc script or a compromised dependency — from doing the same via raw
  SQL.
- **`money_app`'s password is injected into V6 via a Flyway placeholder,
  not hardcoded** (`CREATE ROLE money_app LOGIN PASSWORD '${app_password}'`)
  — reusing `spring.datasource.password` as the placeholder value, so
  Flyway creates the role with the exact password the application will
  actually connect with, and there's only one place either value is set.
  A genuine bug surfaced getting this wired up: `spring.flyway.placeholders`
  is a raw `Map<String,String>` whose *keys* Flyway uses literally, unlike
  most Boot properties, which relax-bind kebab-case YAML keys to
  camelCase. A YAML key of `app-password` (hyphen, matching this project's
  usual style) silently became a *different* placeholder name than the
  SQL file's `${app_password}` (underscore) reference — Flyway's error
  ("No value provided for placeholder: ${app_password}") made the
  mismatch obvious immediately, but it's exactly the kind of thing that's
  easy to introduce silently in a property you can't unit test.
- Virtual threads (`spring.threads.virtual.enabled=true`) are safe to
  enable on the Java 25 toolchain (JEP 491 removed the old
  `synchronized`-block pinning behavior), but they do not increase
  database throughput — the pessimistic locks and the connection pool size
  set that ceiling regardless of the thread model.

## Verification

```bash
./gradlew build
```

`ConstraintPresenceIT` fails if any named constraint from F06/F08/F13 is
missing — verified directly: temporarily added a constraint name that
doesn't exist to its expected list, confirmed the test fails, then
reverted. Runs on H2 as part of `./gradlew build`.

The role-privilege revoke was verified by hand against real Postgres
(`docker compose up -d && ./gradlew bootRun`, F02): V6 applies cleanly,
the running application successfully creates and funds an account as
`money_app` (proving `SELECT`/`INSERT`/`UPDATE` work), and connecting
directly as `money_app` via `psql` confirms `UPDATE ledger_entry`,
`DELETE FROM transfer`, and `UPDATE idempotency_record` all fail with
"permission denied," while `SELECT` on those tables and `UPDATE` on
`account` both still succeed.

The pool-exhaustion → 503 mapping is **not** independently load-tested —
verifying it needs deliberately holding open enough concurrent
transactions to exhaust the pool of 20, which is more test infrastructure
than this feature's scope justifies. Confidence here rests on the
exception hierarchy being correct (`CannotCreateTransactionException`,
`PessimisticLockingFailureException`, and `QueryTimeoutException` are all
real, verified Spring types) and the config values being sensible, not on
an observed 503 under real load.

## Review focus

- That the revoked privileges on the application role don't accidentally
  also block a legitimate operation (e.g. confirm inserts into
  `ledger_entry` still work — only `UPDATE`/`DELETE` should be revoked).
- `ConstraintPresenceIT`'s list of asserted constraints against the actual
  migration files — it should name every constraint from F06, F08, and
  F13, not a subset.
