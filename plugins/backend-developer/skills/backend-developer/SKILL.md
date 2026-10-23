---
name: backend-developer
description: This skill should be used for any backend work on a Kotlin + Spring Boot service — when the user asks to "build a Spring Boot service", "scaffold a backend", "add an endpoint", "add a controller", "add a service", "add a repository", "add a Flyway migration", "add an entity", "handle an error", "return a ProblemDetail", "add Swagger docs", "write an integration test", "set up Testcontainers", "set up ktlint", or to wire JPA, Auth0 JWT validation, configuration, coverage or CI for a Kotlin API. It fixes the package layout, the exception-to-ProblemDetail contract, the persistence and migration rules, and the mandatory lint/test/coverage gates. It is not for frontend work — that is the `frontend-developer` skill.
version: 0.1.0
---

# Backend Developer (Kotlin + Spring Boot)

Build and extend Kotlin services on **Spring Boot 4.1 / Spring Framework 7**, Java 21, Gradle
Kotlin DSL. JPA + Hibernate for access, **Flyway** for schema, Postgres for real, AssertJ for
assertions, Testcontainers and WireMock for integration tests.

**The frontend is a separate service.** This repo owns data, rules and the HTTP contract. It
renders no UI, holds no session, and serves no templates. It validates a bearer token on every
request and trusts nothing else. The OpenAPI document it publishes is the contract the frontend
generates its types from.

## Non-negotiables

- **Kotlin only**, no `!!`, no `lateinit` outside tests, constructor injection only — never field
  `@Autowired`. `allWarningsAsErrors` is on; a warning is a build failure.
- **Flyway owns the schema.** `ddl-auto: validate`, never `update` or `create`. An applied
  migration is immutable. No H2 anywhere — tests run on the same Postgres version as production.
- **Controllers return objects, never `ResponseEntity`.** Status comes from `@ResponseStatus`.
  The advice returns a plain `ProblemDetail`, which Spring already uses to set the status, so
  `ResponseEntity` appears nowhere in the codebase.
- **Every error is an RFC 9457 `application/problem+json` response that names what was wrong.**
  One `GlobalExceptionHandler`, a stable `type` URN per error, a `traceId` on every response, and
  an `errors` array with a JSON Pointer per offending field. No framework default may escape.
- **A 5xx never describes itself.** Fixed safe detail to the client; the cause logged at ERROR
  against the same traceId. No SQL, class names or stack traces in a response, ever.
- **Typed configuration only.** `@ConfigurationProperties` with constructor binding and
  `@Validated`. No `@Value`. Bad config fails startup, not the first request.
- **Tests ship with the code**, unit *and* integration. Coverage is gated at 90% line / 80%
  branch.
- **Every change leaves this green:**
  ```sh
  ./gradlew check        # ktlintCheck + detekt + test + koverVerify
  ```
  Run it before reporting the work finished.

## Where code goes

| Package | Holds | Rules |
| --- | --- | --- |
| `config/` | `@Configuration`, `@ConfigurationProperties`, security, OpenAPI, clients, filters | Wiring only, no business rules |
| `controller/` | `@RestController` | HTTP in/out; returns dtos; no rules, no transactions, no entities |
| `dto/` | Request/response types + mapping extensions | Immutable `data class`, Jakarta validation, Swagger annotations |
| `model/` | The domain | Plain immutable classes, `val` only, **no annotations from JPA, Jackson or Swagger** |
| `repository/` | `@Entity` classes **and** `JpaRepository` interfaces | The only package that may know Hibernate exists |
| `error/` | `ErrorCode`, the exception tree, `GlobalExceptionHandler`, `ProblemDetailFactory` | The single place an exception becomes a response |
| `service/` | Business logic | Owns transactions; the only layer that may throw a `BusinessException` |

Flow: `repository` (entity) → `service` maps to `model` → `controller` maps to `dto`. Entities
never leave `repository`/`service`; dtos never reach `service`.

Mapping lives in **extension functions** beside the type that may see both sides
(`repository/InvoiceMappings.kt`, `dto/InvoiceMappings.kt`). No `mapper/` package, no MapStruct.
Feature subpackages (`controller/invoice/`) are fine once a layer passes ~5 files. Never a
top-level feature package, and never a `util/` dumping ground.

## The error contract

```json
{
  "type": "urn:problem:invoices:validation-failed",
  "title": "Request validation failed",
  "status": 400,
  "detail": "2 fields are invalid: amountCents, customer.email",
  "instance": "/api/v1/invoices",
  "timestamp": "2026-10-23T09:15:00Z",
  "traceId": "4be0baf6ee174255",
  "errors": [
    { "pointer": "#/amountCents",   "field": "amountCents",   "code": "Positive", "message": "must be greater than 0" },
    { "pointer": "#/customer/email", "field": "customer.email", "code": "NotBlank", "message": "must not be blank" }
  ]
}
```

| Throw | Status | When |
| --- | --- | --- |
| `BusinessException` | 400 | The caller's request is wrong and it is not one of the below |
| `NotFoundException` | 404 | The addressed resource does not exist |
| `DuplicateException` | 409 | The write breaks a uniqueness rule |
| `UnprocessableEntityException` | 422 | Well formed, resource exists, a business rule refuses it |
| `SystemException` | 5xx | This service or a dependency failed; never the caller's fault |

Each subclass asserts in its `init` that the `ErrorCode` carries the matching status, so a
mismatch fails at the throw site. Services throw; controllers never catch.

"Validation failed" alone is not an acceptable `detail`. An error names the field: a missing body
field, an unknown property, a wrong type, a bad query parameter or header each come back with a
pointer and a stable `code`. A rejected **value** is never echoed — request bodies carry personal
data and problem responses get logged downstream.

Errors raised **before** the dispatcher cannot reach an `@ExceptionHandler`. Two places cover
them, and both are easy to forget: the security entry points (`auth.md`) and an `ErrorController`
for container error dispatches. See `references/errors.md`.

## Workflow: add an endpoint

1. **Migration** — `V<n>__<desc>.sql`, additive and backwards compatible. Never edit an applied one.
2. **Entity + repository** in `repository/`: `class` not `data class`, `var` fields, every column
   named explicitly, fetch types explicit.
3. **Model** in `model/`: the immutable domain type, plus `toModel()`/`toEntity()`.
4. **Service** in `service/`: the rules, `@Transactional` boundaries, `@PreAuthorize`, and the
   right `BusinessException` subclass for each refusal.
5. **DTOs** in `dto/`: non-nullable Kotlin types, Jakarta constraints, `@Schema` descriptions, and
   `toResponse()`/`toCommand()`.
6. **Controller** in `controller/`: returns the dto, `@ResponseStatus` for anything but 200,
   `@Operation` and an `@ApiResponse` per outcome **including the error ones**.
7. **Tests**: unit-test the service with mocked repositories (MockK + AssertJ, no Spring context);
   integration-test the endpoint through `RestTestClient` against Testcontainers Postgres, with
   every outbound call stubbed in WireMock. Cover each error shape the endpoint can produce.
8. `./gradlew check`.

## Workflow: add a migration

New file, next version, never a change to one already applied — `validate-on-migrate` makes
tampering a startup failure. Keep it additive (new column with a default, new table, new index) so
the previous build keeps working against the new schema and rollout order stops mattering. Update
the entity in the same commit; `ddl-auto: validate` and `SchemaValidationIT` catch the mismatch.

## Definition of done

- [ ] `./gradlew check` green: ktlint, detekt, tests, 90% line / 80% branch coverage.
- [ ] Every new error path returns problem+json with a stable `type` and names the offending field.
- [ ] No entity outside `repository`/`service`; no dto inside `service`; no `ResponseEntity` anywhere.
- [ ] Endpoint documented, error responses included; `contracts/api.yaml` regenerated and committed.
- [ ] New config is typed, validated and documented in `application.yaml`.
- [ ] Migration is additive, and the entity matches it.
- [ ] Authorization asserted in a test, not assumed.

## References

| File | Read it when |
| --- | --- |
| `references/structure.md` | Laying out a project, or unsure where a file belongs |
| `references/errors.md` | Any error path, the handler, or the problem+json contract |
| `references/persistence.md` | Entities, repositories, transactions, Flyway migrations |
| `references/api.md` | Controllers, dtos, validation, Swagger, the exported contract |
| `references/auth.md` | Auth0 resource server, scopes, 401/403 shapes, testing with tokens |
| `references/configuration.md` | `application.yaml`, typed properties, Kubernetes ConfigMaps, probes |
| `references/testing.md` | Writing tests, Testcontainers, WireMock, coverage gates |
| `references/tooling.md` | Gradle setup, Boot 4 renames, ktlint/detekt/Kover, CI, local run |

**`assets/example/` is a complete, verified reference service** — 124 tests, `./gradlew check`
green, run locally against Docker Compose. Copy from it rather than reassembling these patterns
from prose; `assets/example/README.md` maps each file to the pattern it demonstrates and lists the
traps that cost real time while getting it green.
