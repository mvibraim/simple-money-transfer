# F19 — OpenAPI documentation

**Branch:** `feat/19-openapi-documentation` · **Depends on:** F18

## Goal

Give every endpoint a machine-readable, always-current contract without a
hand-maintained spec to keep in sync — generated from the controller and
DTO annotations that already exist, so a spec that drifts from the code is
a build failure, not a stale doc someone eventually notices.

## Scope

- `springdoc-openapi-starter-webmvc-ui:3.1.0` — Swagger UI at
  `/swagger-ui.html`, raw spec at `/v3/api-docs`, both reachable without an
  `X-API-Key` (`config/SecurityConfig.java`'s permitAll list extended
  accordingly).
- `config/OpenApiConfig` — the `apiKey` security scheme (`X-API-Key`
  header), a `ProblemDetail` schema documenting the real shape every
  `ApiExceptionHandler` method actually returns (`type` fixed at
  `about:blank`, `title` the HTTP reason phrase, `errors` present only on
  400 validation failures), and an `OperationCustomizer` that injects 401 +
  500 onto every operation once instead of repeating them on every
  controller method.
- `springdoc.packages-to-scan: com.example.simple_money_transfers.controller`
  (`application.yaml`) — scoped so a `@SpringBootTest`'s full-context
  component scan can't leak a test-only controller (`ProbeController`) into
  the generated spec.
- `springdoc.show-actuator: false` — actuator endpoints stay out of the
  spec; they're operational surface, not API contract.
- Every DTO annotated (`@Schema`, `@NotNull`/`@Positive`/`@Pattern`/etc.
  already drive the generated constraints) across
  `CreateAccountRequest`, `AccountResponse`, `BalanceResponse`,
  `CreateTransferRequest`, `TransferResponse`, `DepositRequest`,
  `WithdrawalRequest`, `LedgerEntryResponse`, `LedgerHistoryResponse`.
- `controller/OpenApiContractIT` — asserts the 8 documented operations are
  reachable unauthenticated at their spec paths, and diffs the live spec
  against the checked-in snapshot `src/test/resources/openapi.json`.

## Explicitly not in this feature

- No client SDK generation — the spec is for human/tool consumption
  (Swagger UI, contract testing), not wired into a codegen pipeline.
- No versioned spec history beyond the single checked-in snapshot; a
  breaking change is a deliberate snapshot update, not something tracked
  across versions.

## Design notes

- **`BigDecimal` fields are documented as `type: string`, not swagger's
  number default.** `SpringDocUtils.getConfig().replaceWithSchema(
  BigDecimal.class, new StringSchema())` runs in a static initializer,
  before springdoc resolves any DTO schema — matching
  `MoneyJacksonConfig`'s actual wire format (F05) rather than the
  misleading default. Every money field (`amount`, `balance`,
  `balanceAfter`) would otherwise document a bare JSON number that the API
  never actually sends.
- **The `ProblemDetail` schema is hand-built, not inferred**, because
  springdoc has no way to see that every `ApiExceptionHandler` method
  calls `ProblemDetail.forStatusAndDetail` and therefore never sets `type`
  or `title` beyond their defaults. Documenting the real, narrower shape
  (`type` always `about:blank`) is more useful to an API consumer than a
  generic RFC 9457 schema that implies fields the API never actually
  varies.
- **`.types(Set.of("object"))`, not the legacy `.type("object")`.**
  swagger-core 2.2.52's OpenAPI 3.1 model represents `type` as a
  `Set<String>`; bridging a manually-built `Schema`'s legacy single-string
  `type` through springdoc's internal JSON-clone step silently drops it
  (with only a logged warning, no failure) — an easy way to ship a schema
  with a missing `type` and not notice until reading the raw JSON.
- **`packages-to-scan` is a correctness fix, not just tidiness.** Without
  it, `OpenApiContractIT`'s own `@SpringBootTest` context — which loads
  `ProbeController` (F03's test-only exception-mapping fixture) — would
  leak that controller's routes into the generated spec, making the test
  validate a spec shape it will never see in production and masking any
  actual drift on the real 8 operations.
- 401 and 500 are injected once via `OperationCustomizer` rather than
  declared on every `@ApiResponse` in every controller method — both apply
  identically everywhere (missing/invalid API key; unmapped exception), so
  repeating them per-endpoint would be the kind of copy-pasted annotation
  that silently goes stale the first time someone adds a ninth endpoint
  and forgets one.

## Verification

```bash
./gradlew build
```

`OpenApiContractIT`: the 8 real operations are present and reachable
without `X-API-Key`; the live spec matches
`src/test/resources/openapi.json` byte-for-byte. Regenerate the snapshot
after a deliberate API change with
`./gradlew test -PupdateOpenApiSnapshot` and commit the diff — see the
test's Javadoc.

Manual: `docker compose up -d && ./gradlew bootRun`, then open
`http://localhost:8080/swagger-ui.html` and confirm every endpoint is
documented with working "Try it out" against a real running instance.

## Review focus

- That `packages-to-scan` genuinely excludes every test-only controller —
  the failure mode if it doesn't is a spec that looks fine in CI (where
  `OpenApiContractIT`'s own context is what's being scanned) but is wrong
  against a real `bootRun`.
- That the `ProblemDetail` schema's `type`/`title` documentation actually
  matches what `ApiExceptionHandler` returns — if a future handler ever
  does call `setType`/`setTitle`, this schema and this doc both go stale
  at the same time and should be updated together.
