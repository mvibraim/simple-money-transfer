---
paths:
  - "src/**/*.java"
---

# Java 25 toolchain

The Gradle toolchain (`java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }`) pins the build to JDK 25, the current LTS (GA September 2025). Finalized features worth reaching for in application code:

- **Scoped Values** (JEP 506, final) — immutable, thread-confined context propagation. Prefer over `ThreadLocal` for request/tenant context, especially once virtual threads are enabled.
- **Flexible Constructor Bodies** (JEP 513, final) — validation and argument prep can now run before `super(...)`/`this(...)`, so fail-fast value objects (e.g. a money/amount type) don't need a static-factory workaround just to validate first.
- **Compact Object Headers** (JEP 519, final) — a product feature in 25, but **off by default**; enable explicitly with `-XX:+UseCompactObjectHeaders` (the `-XX:+UnlockExperimentalVMOptions` that JDK 24 required is gone). Shrinks object headers to 8 bytes — typically 10-20% less heap for live data on object-heavy workloads, so worth setting as a JVM flag on the deployed container. A draft JEP proposes flipping the default on in a future release, but 25 doesn't do it yet.
- G1 is the default collector, and JEP 523 (final in 25) made it the default even in memory-constrained containers, replacing the old Serial GC fallback — don't paste `-XX:+UseSerialGC` container-tuning advice from a pre-25 guide. Generational Shenandoah (JEP 521, final) is production-ready in 25 but only applies if Shenandoah is explicitly selected as the collector; it does not change the G1 default.
- The **AOT cache** (JEP 483, refined by JEPs 514/515 in 25) is the flagship Boot 4 + Java 25 startup optimization — see the `/aot-cache` skill for the training-run procedure; the `Dockerfile`'s runtime stage is the source of truth for how it's wired into this project.
- Module Import Declarations and Compact Source Files (JEPs 511/512, final) suit throwaway scripts, not this application's production or test sources.

Structured Concurrency, Primitive Types in Patterns, the PEM API, Stable Values, and the Vector API are still preview or incubator in 25 — don't take a dependency on a `--enable-preview` API in application code for these.
