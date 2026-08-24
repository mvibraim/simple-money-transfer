---
paths:
  - "src/main/resources/db/migration/**"
  - "compose.yaml"
  - ".env.example"
---

# Local dev datasource

`bootRun` needs a real Postgres, not H2: `docker compose up postgres -d` starts just the database, for the `bootRun` dev loop. `docker compose up` (no service name) builds the app from `Dockerfile` and runs the full stack — app + Postgres — against each other. Copy `.env.example` to `.env` and fill in the secrets `compose.yaml` requires before either form will start. Tests don't need any of this — see `.claude/rules/testing.md`.

Flyway migrations live under `src/main/resources/db/migration/common` (vendor-neutral) and `db/migration/postgresql` (Postgres-only), wired via `spring.flyway.locations: classpath:db/migration/common,classpath:db/migration/{vendor}`. Put a migration in `postgresql/` only if it genuinely needs Postgres-specific SQL — everything else belongs in `common/`, since the H2 test datasource (`MODE=PostgreSQL`) replays `common/` too.
