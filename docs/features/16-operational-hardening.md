# F16 — Operational hardening

**Branch:** `feat/16-operational-hardening` · **Depends on:** F15 · **Docker required:** yes

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
- A second database role for the application (distinct from the Flyway
  migration owner), with `REVOKE UPDATE, DELETE ON ledger_entry FROM
  <app_role>` — `spring.flyway.user`/`spring.flyway.password` configured
  independently of `spring.datasource.*` so Flyway keeps owner privileges
  while the running application does not.
- `ConstraintPresenceIT` — queries `pg_constraint` and `pg_indexes`
  directly and asserts every named constraint and index from F06/F08
  still exists.

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
  thing in the whole test suite that would catch that.
- The application DB role losing `UPDATE`/`DELETE` on `ledger_entry` is the
  privilege-level enforcement of F08's immutability trigger: the trigger
  stops *this codebase's* ORM from rewriting history; the revoked grant
  stops *any* code running as the application — including a future
  ad-hoc script or a compromised dependency — from doing the same via raw
  SQL.
- Virtual threads (`spring.threads.virtual.enabled=true`) are safe to
  enable on the Java 25 toolchain (JEP 491 removed the old
  `synchronized`-block pinning behavior), but they do not increase
  database throughput — the pessimistic locks and the connection pool size
  set that ceiling regardless of the thread model.

## Verification

```bash
docker info
./gradlew build
```

`ConstraintPresenceIT` fails if any named constraint from F06/F08 is
missing (verify by temporarily commenting one out locally and confirming
the test catches it, then restore it). A manual load test (e.g. a burst of
requests beyond the pool size against a deliberately slow query) confirms
503, not a hang or an unrelated-endpoint failure.

## Review focus

- That the revoked privileges on the application role don't accidentally
  also block a legitimate operation (e.g. confirm inserts into
  `ledger_entry` still work — only `UPDATE`/`DELETE` should be revoked).
- `ConstraintPresenceIT`'s list of asserted constraints against the actual
  migration files — it should name every constraint from F06 and F08, not
  a subset.
