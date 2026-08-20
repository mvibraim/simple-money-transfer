# F02 — Local dev database via compose

**Branch:** `feat/02-dev-database-compose` · **Depends on:** F01

## Goal

Let a developer run the app against a real local Postgres with one
command. This is strictly for running the app, never for tests — tests
stay on H2 (F01).

## Scope

- `compose.yaml` (infrastructure only): `postgres:18-alpine`, named volume
  mounted at `/var/lib/postgresql` (not `.../data` — see design note), a
  `pg_isready` healthcheck, and `${POSTGRES_PASSWORD:?...}` so compose
  fails loudly instead of starting with an empty password.
- `.env.example` with placeholder values; `.env` and `*.log` added to
  `.gitignore`.
- `src/main/resources/application.yaml`: adds the Postgres datasource URL,
  username, and password, all read from environment variables with **no
  defaults** — a missing secret fails application startup rather than
  running with a known value.

## Explicitly not in this feature

- No `spring-boot-docker-compose` dependency. With tests entirely on H2
  (F01), that dependency would earn nothing at test time and would only
  add a `bootRun`-time behavior where it silently supplies (and overrides)
  datasource connection details — a common source of "why didn't my
  config change do anything" confusion. `docker compose up -d` followed
  by `./gradlew bootRun` is explicit instead.
- No application container (F17) — adding one to this same file would
  conflict with the explicit `docker compose up -d` workflow above by
  making a plain `compose up` also try to build and start the app.

## Design notes

- **Postgres 18 moved its data directory.** The official image's `PGDATA`
  is now version-scoped (`/var/lib/postgresql/18/docker`) and the declared
  `VOLUME` is the parent, `/var/lib/postgresql`. Mounting the conventional
  `pgdata:/var/lib/postgresql/data` binds nothing — Postgres writes to an
  anonymous volume instead, and the named volume silently stays empty.
  Data then vanishes on `docker compose down` with no error at any point.
  Mount the parent directory.
- This is also the **only** place in the whole project that runs the
  `postgresql/`-only Flyway migrations (F08's trigger, F16's `REVOKE`).
  There is no automated test for their SQL syntax — re-run
  `docker compose up -d && ./gradlew bootRun` after touching any migration
  file, not just ones under `common/`.

## Verification

```bash
docker compose up -d          # starts Postgres only
./gradlew bootRun             # connects using the env-supplied credentials
docker compose down           # then up again — volume must survive
```

## Review focus

- The volume mount path — this is the one line in the file that fails
  silently if wrong.
- No `app` service in `compose.yaml`, and no datasource default values
  anywhere in `application.yaml`.
