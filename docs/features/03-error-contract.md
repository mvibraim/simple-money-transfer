# F03 — Error contract

**Branch:** `feat/03-error-contract` · **Depends on:** F02 · **Docker required:** no

## Goal

Establish one consistent error shape before any endpoint exists, so every
controller written afterward throws domain exceptions into an already-solved
problem instead of hand-rolling its own error response.

## Scope

- `error/ApiExceptionHandler` — `@RestControllerAdvice` returning RFC 9457
  `ProblemDetail` (native support in Spring Framework 7).
- Domain exception hierarchy: `NotFoundException` → 404,
  `BusinessRuleException` → 422, Jakarta Validation failures → 400, anything
  unmapped → 500 with no internal detail (message, stack trace, exception
  class) leaked to the client.
- Slice tests using a minimal test-only `@RestController` under
  `src/test/java` to exercise each mapping without depending on any real
  endpoint.

## Explicitly not in this feature

- No auth-related 401 mapping yet (F04).
- No business exceptions specific to transfers yet (F09) — the hierarchy is
  generic; `InsufficientFundsException` etc. extend `BusinessRuleException`
  when they're introduced.

## Design notes

- `ProblemDetail` over a hand-rolled error DTO because it's a standard
  (RFC 9457), it's what Spring 7 now supports out of the box, and it keeps
  the response shape self-describing (`type`, `title`, `status`, `detail`,
  `instance`) without inventing project-specific field names.
- The fallback 500 handler is deliberately paranoid about not leaking
  detail — a money-movement API is exactly the kind of system where a
  verbose stack trace in a response body is a real information disclosure
  risk, not just untidy.

## Verification

```bash
./gradlew test -PexcludeTags=integration
```

Slice tests assert `Content-Type: application/problem+json` and the
correct status/shape for each exception type, including the fallback case.

## Review focus

- The fallback handler: confirm nothing exception-specific (message, class
  name, cause) reaches the response body.
- That the hierarchy is generic enough that F09's business exceptions won't
  need changes here.
