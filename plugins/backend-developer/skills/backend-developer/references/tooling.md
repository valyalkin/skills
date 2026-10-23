# Build, gates and running locally

Gradle **Kotlin DSL**, Boot **4.1.x**, Kotlin **2.3.x**, Java **21**, Gradle **9.x**. Copy
`../assets/example/build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`,
`gradle.properties`, `.editorconfig`, `detekt.yml`, `compose.yaml` and `githooks/pre-commit`
verbatim.

## Versions live in one file

`gradle/libs.versions.toml` is the version catalogue; `build.gradle.kts` refers to
`libs.plugins.*` and `libs.*`. Anything Spring Boot's BOM manages carries **no version at all**.
Keep the Kotlin version aligned with the `kotlin.version` the BOM manages.

Boot's own plugin version is the only one worth checking by hand at the start of a project; the
rest follow from the BOM.

## The BOM, and the one configuration it misses

```kotlin
dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    ...
    // `developmentOnly` does NOT extend `implementation`, so the BOM's managed versions never
    // reach it. Without this line the dependency resolves with an empty version and only
    // `bootJar` fails - compile and test pass, so it is easy to commit broken.
    developmentOnly(platform(SpringBootPlugin.BOM_COORDINATES))
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
}
```

Gradle's native `platform()` support rather than `io.spring.dependency-management`: one plugin
fewer and faster builds. Use the dependency-management plugin only if property-based version
overrides are genuinely needed.

## Boot 4 renames and module splits

Boot 4 split autoconfiguration into per-feature modules and renamed several starters. A build
assembled from Boot 3 habits resolves but misbehaves:

| Boot 3 | Boot 4 |
| --- | --- |
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `spring-boot-starter-aop` | `spring-boot-starter-aspectj` |
| `spring-boot-starter-oauth2-resource-server` | `spring-boot-starter-security-oauth2-resource-server` |
| `org.flywaydb:flyway-core` alone | **`spring-boot-starter-flyway`** |
| `TestRestTemplate` in `spring-boot-starter-test` | `RestTestClient` in `spring-boot-resttestclient` |

The Flyway one is the expensive mistake: with only `flyway-core` on the classpath the library is
present, **no migration ever runs**, and the first symptom is Hibernate's `ddl-auto: validate`
reporting `missing table [invoices]`. Per-feature test starters also exist
(`spring-boot-starter-webmvc-test`, `-data-jpa-test`, `-security-test`).

Other API moves worth knowing: `ErrorController`/`ErrorAttributes` are now in
`org.springframework.boot.webmvc.error`, and `ClientHttpRequestFactorySettings` became
`HttpClientSettings` with `defaults().withTimeouts(connect, read)`.

## Jackson 3

Boot 4.1 ships **Jackson 3**: the package is `tools.jackson.*`, not `com.fasterxml.jackson.*`, and
the Kotlin module is `tools.jackson.module:jackson-module-kotlin`. Four differences that matter
here:

- `FAIL_ON_UNKNOWN_PROPERTIES` is **disabled by default**. Enable it (`JacksonConfig`) or a
  misspelled field is silently dropped.
- Date features moved to `tools.jackson.databind.cfg.DateTimeFeature`.
- The path on a failure is `JacksonException.path`, whose entries expose `propertyName` (not
  `fieldName`) and `index`.
- `ValueInstantiationException` extends `DatabindException` **directly**, not
  `MismatchedInputException`. Match it explicitly, or a required field rejected by Kotlin's own
  null check is reported as a generic parse failure with no field name.

## Kotlin compiler settings

```kotlin
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        freeCompilerArgs.addAll("-Xjsr305=strict")
        allWarningsAsErrors = true
    }
}
```

`-Xjsr305=strict` makes Spring's nullability annotations binding, so a platform type cannot
silently become non-null. `allWarningsAsErrors` keeps deprecations and unused imports out of the
codebase — including from the build script itself, so prefer `tasks.register<T>("name")` over the
deprecated `by tasks.registering`.

Plugins: `kotlin("plugin.spring")`, `kotlin("plugin.jpa")` (the no-arg constructor) and
`kotlin("plugin.allopen")` configured for `@Entity`/`@MappedSuperclass`/`@Embeddable` so Hibernate
can proxy entities.

## The gates

```kotlin
tasks.named("check") { dependsOn(tasks.named("koverVerify")) }
```

`./gradlew check` = **ktlintCheck + detekt + test + koverVerify**. One command, the same one CI
runs, and the only thing anyone needs to remember.

- **ktlint** (`org.jlleitschuh.gradle.ktlint`) with `ktlint_code_style = ktlint_official`,
  configured in `.editorconfig` — not in the build file. `./gradlew ktlintFormat` fixes nearly
  everything it reports.
- **detekt** with `buildUponDefaultConfig = true`; `detekt.yml` holds only deltas, each with a
  comment saying why. Formatting is disabled there because ktlint owns it; `SpreadOperator` is off
  because it fires on `runApplication<Application>(*args)`; `UnusedParameter` is ignored on
  `@ExceptionHandler` methods, whose signature is the framework's contract.
- **Kover** gates 90% line / 80% branch (`testing.md`).

Suppress a rule at the site with a reason, or change it in `detekt.yml` with a comment. A rule
disabled silently is a rule nobody can reinstate.

## Git hooks

The build installs `githooks/pre-commit` into `.git/hooks`, running `ktlintCheck detekt` — fast
checks only; the suite belongs in CI.

Resolve inputs **outside** the task body. A lambda that touches `layout` or `project`, including in
`onlyIf`, captures a reference to the build script, which the configuration cache cannot
serialise — so use a plain `enabled =` flag computed at configuration time.

## CI

```yaml
- uses: actions/checkout@v4
- uses: actions/setup-java@v4
  with: { distribution: temurin, java-version: '21' }
- uses: gradle/actions/setup-gradle@v4
- run: ./gradlew check
```

GitHub-hosted runners provide Docker, so Testcontainers works unchanged. Upload
`build/reports/kover` and the test results on failure. Fail the build on a dirty
`contracts/api.yaml` (`git diff --exit-code contracts/api.yaml`) so a contract change cannot be
forgotten.

## Running locally

```sh
./gradlew bootRun --args='--spring.profiles.active=local'
```

`spring-boot-docker-compose` starts `compose.yaml`, waits for the health check, injects the
datasource and stops the container on exit. No manual `docker compose up`, nothing left running.
See `configuration.md` for the project-name and ephemeral-port rules that stop it colliding with
other services.

Then:

```sh
curl -s localhost:8080/actuator/health
curl -s localhost:8080/v3/api-docs.yaml | head
open http://localhost:8080/swagger-ui.html
```

Two environment traps:

- **`postgres:18`** wants its tmpfs/volume mounted at `/var/lib/postgresql`, **not**
  `/var/lib/postgresql/data`. With the old path the container exits with "there appears to be
  PostgreSQL data in /var/lib/postgresql/data (unused mount/volume)".
- **WireMock and the JDK HTTP client.** Boot 4's default client attempts an h2c upgrade on plain
  `http://`, which WireMock's Jetty answers with `RST_STREAM`. It surfaces as a mystifying "Stream
  cancelled" I/O error — and, since every client failure becomes a `SystemException`, as a 503 in a
  test. Set `.http2PlainDisabled(true)` on the WireMock server.
