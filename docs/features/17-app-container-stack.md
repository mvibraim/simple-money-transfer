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
  credentials, API keys) supplied via the `.env` file from F02, no
  defaults for anything sensitive.
- Application image built via `./gradlew bootBuildImage` (Cloud Native
  Buildpacks) rather than a hand-written `Dockerfile`.
- `README.md` — the actual "how to run this locally" instructions:
  prerequisites, `.env` setup, the compose command, a smoke-test `curl`
  sequence.

## Explicitly not in this feature

- No CI/CD pipeline, no production deployment manifests (Kubernetes,
  Terraform, etc.) — purely local developer/reviewer ergonomics, matching
  the original ask.

## Design notes

- **Kept in a separate file from `compose.yaml`, not merged into it.**
  F02's `spring-boot-docker-compose` support runs `docker compose up`
  automatically during `./gradlew bootRun`. If the app service lived in
  the same file that Boot's compose integration reads, every local
  `bootRun` would additionally try to start a container of the application
  itself — confusing at best, and actively wrong when iterating on code
  that the container image doesn't yet contain. The two-file split keeps
  "run Postgres for local development" and "run the whole packaged stack"
  as clearly separate operations.
- Buildpacks over a hand-rolled Dockerfile: no Dockerfile to keep in sync
  with the Gradle/Java toolchain version as it changes, and buildpacks
  produce reasonable JVM memory/GC defaults and layer caching without
  bespoke multi-stage build logic to maintain.

## Verification

```bash
cp .env.example .env   # edit secrets
docker compose -f compose.yaml -f compose.app.yaml up --build
```

Then the full smoke test from the roadmap's end-to-end verification
section: create two accounts, fund one, transfer between them, replay the
same idempotent request, and confirm each documented failure mode (fund
shortfall, currency mismatch, missing API key, reused idempotency key with
a different body) returns the expected status.

## Review focus

- That `compose.app.yaml` genuinely requires being passed explicitly
  alongside `compose.yaml` — a reviewer should confirm `docker compose up`
  with no `-f` flags still only starts Postgres, preserving F02's
  dev-loop behavior.
- The README's smoke-test sequence actually works read-through, end to
  end, on a machine with nothing but Docker and this repo checked out.
