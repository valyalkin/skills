# backend-developer

Builds Kotlin + Spring Boot backends with one predictable shape, so a new service — or a new
endpoint in an old one — lands where a reader expects it.

Targets **Spring Boot 4.1 / Spring Framework 7**, Kotlin 2.3, Java 21, Gradle Kotlin DSL. JPA and
Hibernate for access with **Flyway** owning the schema (`ddl-auto: validate`, never H2), Auth0 as
an OAuth2 resource server, and springdoc publishing the OpenAPI contract the frontend generates its
types from.

The opinionated parts:

- **A fixed package layout**: `config`, `controller`, `dto`, `model`, `repository`, `error`,
  `service`. JPA entities sit in `repository/` beside the `JpaRepository`, so everything
  Hibernate-aware is in one package and `model/` stays immutable and annotation-free.
- **Controllers return objects, never `ResponseEntity`.** The error advice returns a plain
  `ProblemDetail`, which Spring already uses to set the status — so that type appears nowhere.
- **Every error is an RFC 9457 `problem+json` response that names the offending field**, with a
  stable `type` URN, a `traceId`, and a JSON Pointer per violation. `BusinessException` (400) with
  `NotFoundException` (404), `DuplicateException` (409) and `UnprocessableEntityException` (422),
  plus `SystemException` (5xx) whose cause is logged and never returned. The framework's own
  exceptions are mapped explicitly, and the two paths an `@ExceptionHandler` cannot see — the
  security filter chain and container error dispatches — are covered too.
- **Tests and gates are not optional**: unit tests with AssertJ and MockK, integration tests on
  `@SpringBootTest` + `RestTestClient` + Testcontainers Postgres + WireMock, ktlint, detekt, and
  Kover at 90% line / 80% branch. One command: `./gradlew check`.
- **Runs locally in one command.** `bootRun` starts its own Postgres through Docker Compose, on an
  ephemeral port under a project-scoped name, so it cannot collide with another service.

## Skills

| Skill | Triggers on |
| --- | --- |
| `backend-developer` | "build a Spring Boot service", "add an endpoint/controller/repository", "add a Flyway migration", "return a ProblemDetail", "add Swagger docs", "write an integration test", "set up ktlint", Kotlin API scaffolding |

The skill body is a lean workflow; the detail lives in `references/` (structure, errors,
persistence, api, auth, configuration, testing, tooling), with a **complete verified reference
service** in `assets/example/` — 124 tests, `./gradlew check` green, run locally against Docker.
Its README maps every file to the pattern it demonstrates and lists the traps found while getting
it green.

Pairs with [`frontend-developer`](../frontend-developer): that skill assumes a separate backend
API, validated tokens and generated API types; this one is the other half.

## Install

```
/plugin marketplace add valyalkin/skills
/plugin install backend-developer@alex-skills
```
