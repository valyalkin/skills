# Reference implementation

A complete, working slice of the architecture this skill describes. Everything here was run:
`./gradlew check` — ktlint, detekt, **124 tests** (unit + integration) and Kover's 90% line / 80%
branch gates — passes, `./gradlew build` produces a bootable jar, and the service was started
locally against Docker Compose and exercised over HTTP.

Copy from it rather than reassembling the patterns from prose. The domain (invoices, customers) is
the same one the `frontend-developer` skill's example uses, so the two compose into one working
system: this service publishes `contracts/api.yaml`, and that is the file the frontend generates
its types from.

```sh
./gradlew check                                        # the gate
./gradlew bootRun --args='--spring.profiles.active=local'   # starts its own Postgres
```

Requires Docker for both. Replace `com.example.invoices` with your package and keep the shapes.

## What each file demonstrates

| File | Pattern |
| --- | --- |
| `build.gradle.kts` | Boot 4 starters, native BOM `platform()`, the `developmentOnly` trap, `allWarningsAsErrors`, allopen for entities, the `check` gate |
| `gradle/libs.versions.toml` | Every version in one place; nothing the BOM manages |
| `detekt.yml`, `.editorconfig` | Deltas only, each with the reason; ktlint owns formatting |
| `compose.yaml` | Project-scoped name, **no fixed host port**, Postgres 18's mount path |
| `error/ErrorCode.kt` | The error catalogue: status + stable `type` URN + title |
| `error/BusinessException.kt` | The 400 base and the 404/409/422 subclasses, each policing its own status in `init` |
| `error/SystemException.kt` | 5xx, whose message is for the log and never the response |
| `error/FieldViolation.kt` | One `errors` entry: pointer, field, machine code, message — and no rejected value |
| `error/ViolationExtractor.kt` | Every framework validation failure → a named field |
| `error/ProblemDetailFactory.kt` | The one place a problem is built; the safe 5xx detail |
| `error/GlobalExceptionHandler.kt` | One handler per type, all returning plain `ProblemDetail` |
| `error/ProblemErrorController.kt` | Container error dispatches, which no `@ExceptionHandler` can see |
| `config/RequestIdFilter.kt` | traceId in MDC + response header + problem body, ordered **ahead of** Spring Security |
| `config/SecurityConfig.kt` | Auth0 resource server, explicit audience validation, **both** entry points, UTF-8 |
| `config/InvoiceProperties.kt` | Constructor-bound, `@Validated`, no hidden defaults, no `@Value` |
| `config/JacksonConfig.kt` | The JSON settings the error contract depends on |
| `config/RestClientConfig.kt` | Outbound client with explicit timeouts |
| `config/OpenApiConfig.kt` | Bearer scheme and the relative server that keeps the contract stable |
| `repository/InvoiceEntity.kt` | `class` not `data class`, `var` fields, explicit columns, `@Version` |
| `repository/InvoiceMappings.kt` | entity ↔ model as extension functions |
| `model/Invoice.kt` | The immutable, annotation-free domain type |
| `service/InvoiceService.kt` | Rules, transaction boundaries, `@PreAuthorize`, the lost-race 409 |
| `service/PaymentGatewayClient.kt` | Every upstream failure becomes a `SystemException` |
| `dto/CreateInvoiceRequest.kt` | Non-nullable types + `@field:` constraints + `@Schema` |
| `dto/PageResponse.kt` | The service's own page envelope instead of Spring Data's `Page` |
| `controller/InvoiceController.kt` | Returns objects, `@ResponseStatus`, an `@ApiResponse` per outcome including errors |
| `db/migration/V1`, `V2` | Additive, backwards-compatible migrations; a partial unique index as a business rule |
| `support/AbstractIntegrationTest.kt` | One shared Postgres + WireMock, `@ServiceConnection`, `RestTestClient` |
| `support/JwtTestTokens.kt` | Real signed JWTs with a WireMock-served JWKS — no mocked decoder |
| `error/GlobalExceptionHandlerIT.kt` | **One real request per error shape**, asserting pointers, codes and non-leakage |
| `config/SecurityIT.kt` | Every token rejection path, and that a 401 is still traceable |
| `repository/SchemaValidationIT.kt` | Flyway history, columns, indexes and constraints |
| `config/OpenApiExportIT.kt` | Exports `contracts/api.yaml`; fails if an endpoint lacks its error responses |
| `failures/FailureController.kt` | Provoking a 500 / missing param / missing header, from outside the scan root |

## Findings baked into this code

Each of these cost real time while getting the suite green, and each is a line of code you would
otherwise write wrong.

**Only `flyway-core` means no migrations run.** Boot 4 split autoconfiguration into per-feature
modules; the Flyway autoconfiguration is in `spring-boot-starter-flyway`. With the bare library the
first symptom is Hibernate reporting `missing table [invoices]`, which looks like a schema problem
rather than a dependency one.

**`developmentOnly` does not extend `implementation`,** so the BOM's managed versions never reach
it. `spring-boot-docker-compose` then resolves with an empty version — and only `bootJar` fails, so
compile and test stay green and it is easy to commit broken.

**Returning a plain `ProblemDetail` sets the response status.** Spring reads the `status` field, so
the advice needs no `ResponseEntity` and the whole codebase can keep that type out.

**The request-id filter must be ordered ahead of Spring Security's chain** (-110 against its -100).
A filter at the same order or later never runs for a rejected request, so every 401 and 403 comes
back with `traceId: "unknown"` and no `X-Request-Id` header — on exactly the requests somebody is
debugging. Two `SecurityIT` tests fail if that number changes back; verified by changing it.

**The resource server has its own entry point.** An invalid, expired or wrong-audience token is
rejected inside `BearerTokenAuthenticationFilter`, which ignores the one configured on
`exceptionHandling`. Wire only the latter and a *missing* token yields a proper problem while an
*invalid* one yields an empty body: same status, two different shapes.

**Spring does not validate `aud`.** `JwtValidators.createDefaultWithIssuer` checks the issuer and
the timestamps only; without an explicit audience validator this service accepts any token the
tenant minted for any other API. And because `Predicate<T>` is a Java type, Kotlin types the claim
as non-null while Spring passes `null` for an absent `aud` — so the predicate must take a nullable
list or the rejection path throws an NPE and surfaces as a 500.

**A hand-written error response needs its charset set.** The security entry points bypass Spring's
message converters, and a servlet response defaults to ISO-8859-1 — the one error path that would
otherwise answer in a different charset from every other.

**Jackson 3 changed four things that this error contract depends on:** the package is
`tools.jackson.*`; `FAIL_ON_UNKNOWN_PROPERTIES` is off by default; date features live in
`DateTimeFeature`; and `ValueInstantiationException` extends `DatabindException` *directly*, not
`MismatchedInputException` — so without its own branch a required field rejected by Kotlin's null
check is reported as a generic parse failure with no field name.

**Status codes have aliases.** `HttpStatus.resolve(413)` returns `CONTENT_TOO_LARGE`, not
`PAYLOAD_TOO_LARGE`, and an identity comparison against the wrong constant silently falls through.
`ProblemErrorController` matches on the numeric code and reports the status the container chose
rather than substituting the `ErrorCode`'s own.

**The component scan covers the test classpath.** A `@RestController` in test sources under the
application's package is registered twice — once by the scan, once by the `@Bean` — and every
context in the suite fails with an ambiguous mapping. Dropping the stereotype is not a fix either:
Spring Framework 7 detects a handler by `@Controller`, so a class with only `@RequestMapping` is
never mapped. The test-only controller therefore lives in `com.example.failures`, outside the scan
root.

**WireMock and Boot 4's default HTTP client disagree about h2c.** The JDK client attempts an
upgrade on plain `http://`; WireMock's Jetty answers `RST_STREAM`. It presents as "Stream
cancelled" and, since client failures become `SystemException`, as an unexplained 503.
`.http2PlainDisabled(true)` settles it.

**Testcontainers 2.x** renamed the module to `org.testcontainers:testcontainers-postgresql` and
dropped the self-referential generic: it is `PostgreSQLContainer`, not `PostgreSQLContainer<*>`.

**`postgres:18` moved its data directory.** The mount belongs at `/var/lib/postgresql`; at the old
`/var/lib/postgresql/data` the container exits with "unused mount/volume".

**springdoc writes an absolute server URL** — the port it happens to be listening on — so an
exported contract differs on every run. Declaring one relative server keeps the committed file
stable.

**`PageImpl` recomputes the total** when the content list does not match the page size, which turns
an innocent-looking page test into a confusing failure.

**Gradle's configuration cache rejects script references in task bodies,** `onlyIf` included.
Compute inputs outside the task and use a plain `enabled =` flag.

## Not included

`.gitignore` for a standalone copy (`build/`, `.gradle/`), a `Dockerfile` (`./gradlew bootBuildImage`
covers it), and Kubernetes manifests — the skill states the ConfigMap contract but ships no YAML.
