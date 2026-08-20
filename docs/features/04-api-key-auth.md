# F04 — API key authentication

**Branch:** `feat/04-api-key-auth` · **Depends on:** F03 · **Docker required:** no

## Goal

Put authentication in place before any endpoint exists, so every endpoint
written from F06 onward is tested with the header from day one instead of
retrofitting auth across an already-large test suite later.

## Scope

- `build.gradle`: `spring-boot-starter-security` +
  `spring-boot-starter-security-test`.
- `config/AppProperties` — binds `app.security.api-keys[].{id,key}` as a
  `List<ApiKeyEntry>`, validated (`@NotEmpty`, minimum key length) so a
  missing or weak secret fails application startup rather than running
  insecurely.
- `config/ApiKeyAuthFilter` — reads `X-API-Key`, compares against configured
  keys using `MessageDigest.isEqual`, sets the matched key's `id` as the
  authenticated principal (this becomes the idempotency `client_id` in
  F13).
- `config/SecurityConfig` — stateless (`SessionCreationPolicy.STATELESS`),
  CSRF disabled (no cookies are ever used), `/actuator/health` permitted,
  everything else requires authentication.
- Actuator lockdown: `management.endpoints.web.exposure.include=health,info,metrics`
  (never `env`, `configprops`, `httpexchanges`),
  `management.endpoint.health.show-details=never`.

## Explicitly not in this feature

- No per-account authorization — the key authenticates the *client*, not an
  account owner (see the roadmap's "out of scope" section).
- No endpoints yet to actually protect (F06+) — tested against the same
  test-only controller pattern from F03.

## Design notes

- **`MessageDigest.isEqual`, not `String.equals`.** `String.equals`
  short-circuits on the first differing byte, which makes response timing a
  measurable side channel for guessing the key byte by byte. One-line fix,
  easy to miss in review otherwise.
- **Keys as a list, not a map.** `Map<String,String>` binding is tempting
  for `id -> key`, but map keys containing dots or dashes don't survive
  Spring's relaxed environment-variable binding reliably. A list with
  index-based env vars (`APP_SECURITY_APIKEYS_0_ID`,
  `APP_SECURITY_APIKEYS_0_KEY`) always works.
- `ApiKeyEntry.toString()` is overridden to redact the key value, so an
  accidental log of the properties object doesn't leak a credential.

## Verification

```bash
./gradlew test -PexcludeTags=integration
```

Missing key → 401; wrong key → 401; valid key → 200; all as
`application/problem+json` via F03. `/actuator/health` reachable with no
key.

## Review focus

- The `MessageDigest.isEqual` comparison specifically.
- That `/actuator/health` is the *only* unauthenticated path, and that its
  `show-details` setting doesn't leak the datasource URL.
