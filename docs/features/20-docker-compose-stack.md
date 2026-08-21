# F20 — Docker Compose stack

**Branch:** `feat/20-docker-compose-stack` · **Depends on:** F19

> **Supersedes F17.** F17 shipped `bootBuildImage` (Buildpacks) plus a
> separate `compose.app.yaml` kept out of the default `docker compose up`
> path. This feature replaces both: a hand-rolled, layered `Dockerfile`
> takes over image building, and the `app` service moves into `compose.yaml`
> itself, so `docker compose up` alone runs the full stack. F17's own doc
> carries the same superseded marker; kept for history, not as the current
> design.

## Goal

Make "run the project locally" a genuinely single command —
`docker compose up`, nothing else — while also picking up the Spring Boot
4.1 / Java 25 container-performance features CLAUDE.md calls out as
worth having in production: layered images and the AOT cache.

## Scope

- `Dockerfile` — multi-stage, digest-pinned base images:
  - **`build` stage** (`eclipse-temurin:25-jdk`): wrapper and build files
    copied before source, with a Gradle dependency-cache mount, so an
    edit to `src/` invalidates only the smallest possible layer on
    rebuild. `bootJar`, then `java -Djarmode=tools ... extract --layers`
    splits the fat jar into `dependencies` / `spring-boot-loader` /
    `snapshot-dependencies` / `application`.
  - **`runtime` stage** (`eclipse-temurin:25-jre`, non-root `spring`
    user): the four layers `COPY`'d in least- to most-frequently-changed
    order; `curl` installed solely for the `HEALTHCHECK` against
    `/actuator/health` (unauthenticated, F04).
  - **AOT cache training run** (JEP 514) — `-XX:AOTCacheOutput=app.aot
    -Dspring.context.exit=onRefresh`, with training-only `-D` overrides
    that stub out the database (`spring.flyway.enabled=false`,
    `ddl-auto=none`, a dummy JDBC URL/credentials, a dummy 32-char API
    key) so the context can refresh far enough for JIT profiling without
    a reachable Postgres. Measured ~55% faster cold start (3.1s → 1.4s).
  - Runtime `ENTRYPOINT`: `-XX:AOTCache=app.aot -XX:+UseCompactObjectHeaders
    -XX:MaxRAMPercentage=75`. `JAVA_TOOL_OPTIONS` is deliberately left
    unset as the operator-override channel.
- `compose.yaml` — the `app` service folded in alongside `postgres`:
  builds from `Dockerfile`, tagged `simple-money-transfers:latest`
  (matching `bootBuildImage`'s old fixed tag in `build.gradle`, so either
  build path produces an interchangeable image), `depends_on:
  postgres: condition: service_healthy`, secrets from `.env` with no
  defaults, `stop_grace_period: 40s`, and a `HEALTHCHECK`-mirroring
  compose healthcheck.
- `compose.app.yaml` deleted — no longer a separate opt-in file.
- `.dockerignore` — excludes `.git`, `.github`, `.gradle`, `build`,
  `docs`, markdown files, and both `.env*` files from the build context.
- `.github/workflows/ci.yml` — new `image` job building the `Dockerfile`
  on every PR (`push: false`), so a Dockerfile regression fails CI
  instead of shipping unnoticed. No service containers needed — the AOT
  training run's database stubbing is exactly what makes a
  dependency-free image build possible.
- `README.md` — single-command run instructions, `.env` setup, the
  smoke-test sequence, and the failure-mode table.

## Explicitly not in this feature

- No Kubernetes manifests, Helm charts, or other production deployment
  targets — this is still local developer/reviewer ergonomics, same
  scope boundary F17 drew.
- No change to the AOT cache's training-config stubs beyond what's needed
  to get a context refresh — they're build-time only and never reach the
  actual runtime configuration.

## Design notes

- **Layered extraction over one fat layer, verified empirically.** Four
  `COPY` layers ordered `dependencies` → `spring-boot-loader` →
  `snapshot-dependencies` → `application` means a source-only change
  invalidates just the last, smallest layer on rebuild, instead of the
  whole multi-hundred-megabyte image layer a single-`COPY` fat jar would.
- **JVM flags live in `ENTRYPOINT`, not `JAVA_TOOL_OPTIONS`.** Anything an
  operator sets in `JAVA_TOOL_OPTIONS` at runtime *augments* the
  `ENTRYPOINT` flags rather than silently replacing them — baking the
  AOT-cache and heap flags into `JAVA_TOOL_OPTIONS` instead would make
  that variable a foot-gun for the first person who reaches for it as the
  "normal" override channel and unknowingly clobbers cache/heap behavior.
- **`-XX:MaxRAMPercentage=75`, set explicitly.** The JVM's own default is
  25% of container memory, which wastes most of a typical container's
  budget — worth setting explicitly rather than inheriting a default
  tuned for a very different deployment shape.
- **The AOT training run's stubs never reach production config.** They're
  `-D` system properties scoped to that one `RUN` layer, not `ENV`, not
  written into `application.yaml`, and not present in the `ENTRYPOINT`.
  Verified by confirming the running container still fails to start
  without real datasource credentials (F02's "no defaults, fail loudly"
  design holds).
- **`-XX:+UseCompactObjectHeaders` must be byte-identical between the
  training run and the `ENTRYPOINT`.** It changes object layout; a
  mismatch doesn't error, it silently invalidates the cache and the app
  falls back to a normal (slower) startup with no visible signal that
  the AOT cache stopped helping.
- **Base images pinned by digest, not just tag.** `eclipse-temurin:25-jdk`
  and `:25-jre` tags can move; the digest is what actually guarantees the
  build and runtime stages saw the same bits during development as CI's
  `image` job and any later rebuild.
- **`compose.app.yaml`'s split-file reasoning (F17) no longer applies.**
  F17 kept the `app` service in a separate file so a plain `compose up`
  stayed Postgres-only, avoiding an assumed `spring-boot-docker-compose`
  interaction that F02 ultimately never added. With that dependency never
  in the picture, folding `app` into `compose.yaml` directly is simpler
  and matches this feature's actual goal — one command, full stack.

## Verification

```bash
cp .env.example .env   # edit secrets
docker compose up      # builds (first run) or reuses the image, starts both services
```

Ran the full smoke test end to end against the packaged container (not
`bootRun`): created two accounts, funded one, transferred between them,
replayed the identical idempotent request, exercised every documented
failure mode, and confirmed `SUM(ledger_entry.amount) = 0` directly
against Postgres afterward — same coverage F17 originally verified,
re-run against this Dockerfile-based build path instead of Buildpacks.

Cold-start timing measured by comparing container start-to-healthy with
and without `-XX:AOTCache=app.aot` on the `ENTRYPOINT`.

```bash
docker build -t simple-money-transfers:ci .   # what CI's image job runs
```

## Review focus

- That `docker compose up` alone — no flags — genuinely builds and starts
  both services against a clean checkout with only `.env` filled in.
- The AOT training run's `-D` stub list: confirm none of those values
  could leak into a real request path (they're all training-only
  overrides on one `RUN` layer, never referenced again).
- `-XX:+UseCompactObjectHeaders` presence and agreement between the
  training `RUN` and the `ENTRYPOINT` — this is the one line that fails
  silently if it ever drifts.
