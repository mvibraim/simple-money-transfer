# F02 — Local dev database via compose

**Branch:** `feat/02-dev-database-compose` · **Depends on:** F01 · **Docker required:** yes

## Goal

Let a developer run `./gradlew bootRun` and get a working Postgres with zero
manual setup, without coupling the dev database to how the app itself ships
(that's F17).

## Scope

- `compose.yaml` (infrastructure only): `postgres:18-alpine`, named volume
  mounted at `/var/lib/postgresql` (not `.../data` — see design note), a
  `pg_isready` healthcheck, and `${POSTGRES_PASSWORD:?...}` so compose fails
  loudly instead of starting with an empty password.
- `build.gradle`: `developmentOnly 'org.springframework.boot:spring-boot-docker-compose'`.
- `.env.example` with placeholder values; `.env` and `*.log` added to
  `.gitignore`.

## Explicitly not in this feature

- No application container (F17) — adding one to this same file would make
  Boot's compose support start a container of the app itself on every
  `bootRun`.

## Design notes

- **Postgres 18 moved its data directory.** The official image's `PGDATA`
  is now version-scoped (`/var/lib/postgresql/18/docker`) and the declared
  `VOLUME` is the parent, `/var/lib/postgresql`. Mounting the conventional
  `pgdata:/var/lib/postgresql/data` binds nothing — Postgres writes to an
  anonymous volume instead, and the named volume silently stays empty. Data
  then vanishes on `docker compose down` with no error at any point. Mount
  the parent directory.
- `spring-boot-docker-compose` is `developmentOnly` so it can never ship,
  and it skips itself under `spring.docker.compose.skip.in-tests` (the
  default), so it won't fight the Testcontainers setup from F01.
- While Boot's compose support is active, it *supplies* datasource
  connection details automatically — so any URL/username/password set in
  `application.yaml` is ignored during `bootRun`. Worth a comment in the
  yaml since it's a common source of "why didn't my config change do
  anything" confusion.

## Verification

```bash
docker compose up -d          # starts Postgres only
./gradlew bootRun             # connects automatically, no manual config
docker compose down           # then up again — volume must survive
```

## Review focus

- The volume mount path — this is the one line in the file that fails
  silently if wrong.
- No `app` service in `compose.yaml`.
