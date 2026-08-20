# F17 — App container stack

**Branch:** `feat/17-app-container-stack` · **Depends on:** F16

## Goal

Make "run the project locally" a single command for someone who isn't a
Java developer — the last item from the original ask, and deliberately the
very last feature, since it should package a project that's already
correct rather than being developed against.

## Scope

- `compose.app.yaml` — adds an `app` service on top of F02's `compose.yaml`
  (`docker compose -f compose.yaml -f compose.app.yaml up`), `depends_on:
  postgres: condition: service_healthy`, application secrets (DB
  credentials, API keys) supplied via `.env`, no defaults for anything
  sensitive. Explicitly overrides `SPRING_DATASOURCE_URL` to point at the
  `postgres` service hostname rather than inheriting `.env`'s
  `localhost`-based value — see design notes.
- `build.gradle`: `bootBuildImage { imageName = 'simple-money-transfers:latest' }`
  — a fixed, predictable tag rather than the default group/name/version-
  derived one, so `compose.app.yaml` can reference it directly.
- `README.md` — prerequisites, `.env` setup, the compose command, a
  smoke-test `curl` sequence, and the expected status for every documented
  failure mode.

## Explicitly not in this feature

- No CI/CD pipeline, no production deployment manifests (Kubernetes,
  Terraform, etc.) — purely local developer/reviewer ergonomics, matching
  the original ask.

## Design notes

- **Kept in a separate file from `compose.yaml`, not merged into it** —
  so that a plain `docker compose up` (no `-f` flags) still only starts
  Postgres, preserving F02's dev-loop behavior, and the full packaged
  stack stays an explicit opt-in
  (`-f compose.yaml -f compose.app.yaml`). (The original reasoning for
  this split assumed `spring-boot-docker-compose` was in use; F02
  ultimately dropped that dependency, so the real reason is simpler than
  first planned — but the two-file structure it called for turned out to
  be the right shape anyway.)
- **`SPRING_DATASOURCE_URL` is set explicitly in `compose.app.yaml`, not
  inherited from `.env`.** Inside the compose network the app reaches
  Postgres by the service name `postgres`; `.env`'s
  `jdbc:postgresql://localhost:5432/...` is correct only for `bootRun` on
  the host, where `localhost` reaches the container through its published
  port. Passing that same URL straight through to the containerized app
  would have it try to connect to itself.
- Buildpacks over a hand-rolled Dockerfile: no Dockerfile to keep in sync
  with the Gradle/Java toolchain version as it changes, and buildpacks
  produce reasonable JVM memory/GC defaults and layer caching without
  bespoke multi-stage build logic to maintain. Verified this works in the
  reference environment specifically because it was a real open question:
  Testcontainers (F00) fails here on a docker-java container-create bug,
  and `bootBuildImage` also creates containers as part of the buildpacks
  lifecycle — but it goes through a different Docker client path and
  built the image without issue.
- **Found and fixed while doing this feature's own end-to-end run, not
  strictly in scope but too easy to leave in:** Boot's
  `UserDetailsServiceAutoConfiguration` was still active and logging a
  generated in-memory-user password on every startup
  (`Using generated security password: ...`), even though this service is
  API-key-only (F04) and never uses username/password auth at all. Excluded
  it on the `@SpringBootApplication` annotation — dead configuration that
  only invites a reviewer to wonder what it's for.

## Verification

```bash
cp .env.example .env   # edit secrets
./gradlew bootBuildImage
docker compose -f compose.yaml -f compose.app.yaml up -d
```

Ran the full smoke test end to end against the actual packaged container
(not `bootRun`): created two accounts, funded one, transferred between
them, replayed the identical idempotent request (byte-identical response,
confirmed via the ledger history endpoint showing exactly one debit/credit
pair), and exercised every documented failure mode — insufficient funds,
currency mismatch, missing API key, reused idempotency key with a
different body — each returning the status the README now documents.
Confirmed `SUM(ledger_entry.amount) = 0` directly against Postgres
afterward.

## Review focus

- That `compose.app.yaml` genuinely requires being passed explicitly
  alongside `compose.yaml` — a reviewer should confirm `docker compose up`
  with no `-f` flags still only starts Postgres, preserving F02's
  dev-loop behavior.
- The README's smoke-test sequence actually works read-through, end to
  end, on a machine with nothing but Docker and this repo checked out.
