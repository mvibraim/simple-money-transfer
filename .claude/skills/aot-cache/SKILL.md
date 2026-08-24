---
description: Generate or refresh the Java 25 AOT cache for this app outside the container build, and explain how the Dockerfile's training run works
disable-model-invocation: true
---

The Java 25 AOT cache (JEP 483/514/515) is this stack's highest-leverage
startup optimization — Spring's own benchmarks show ~40%+ faster cold start.
It is already wired into the container build; this skill is for running the
same procedure by hand (e.g. profiling startup locally, outside Docker) or
for explaining the mechanism.

## Source of truth

`Dockerfile` lines 52-77 are the source of truth, not this file. Read them
before touching anything — the training run's `-D` overrides (faking out a
reachable Postgres so the context can refresh without a real database) and
the `-XX:+UseCompactObjectHeaders` requirement are commented in place there.
If the two ever disagree, the Dockerfile wins and this skill is stale.

## The procedure

The cache is built against the *extracted* jar layout, not the fat jar:

```bash
java -Djarmode=tools -jar app.jar extract --destination application
cd application
java -XX:AOTCacheOutput=app.aot -Dspring.context.exit=onRefresh -jar app.jar   # training run
java -XX:AOTCache=app.aot -jar app.jar                                        # production start
```

`spring.context.exit=onRefresh` runs the context through bean init and JIT
profiling, then exits before any request-handling logic runs.

## The two things that break this

1. **This app's context cannot refresh without a reachable Postgres**
   (Flyway migrates on startup, `ddl-auto: validate`). The training run must
   pass the same training-only `-D` overrides the Dockerfile does
   (`spring.flyway.enabled=false`, an explicit Hibernate dialect,
   `hibernate.boot.allow_jdbc_metadata_access=false`, dummy datasource
   credentials, a dummy API key). These never reach the real runtime config —
   they exist only to get the context through `onRefresh` without a database.
2. **`-XX:+UseCompactObjectHeaders` must be byte-identical between the
   training run and the production start.** It changes object layout; a
   mismatch silently invalidates the cache instead of erroring.

## When to regenerate

Any application or JDK change invalidates `app.aot`. In this project that
happens automatically — it's a `RUN` step baked into the `Dockerfile`'s image
build, not a file to hand-maintain. Use this skill only for local
experimentation outside the container, and don't commit an `app.aot` you
generate this way.
