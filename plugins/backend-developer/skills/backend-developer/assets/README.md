# Assets

`example/` is the verified reference service: a complete, runnable Kotlin + Spring Boot 4 project
whose `./gradlew check` is green. It is the thing to copy.

There are deliberately no duplicate config templates here. The files worth copying verbatim are
the ones in `example/` itself, and keeping a single copy means they cannot drift from the project
that proves they work:

| Copy from | For |
| --- | --- |
| `example/build.gradle.kts` | Plugins, Boot 4 starters, BOM wiring, compiler flags, the `check` gate |
| `example/gradle/libs.versions.toml` | The version catalogue |
| `example/settings.gradle.kts`, `example/gradle.properties` | Project name, Gradle switches |
| `example/.editorconfig` | ktlint's configuration |
| `example/detekt.yml` | detekt deltas, each with its reason |
| `example/compose.yaml` | Local Postgres that cannot collide with another service |
| `example/githooks/pre-commit` | The commit-time gate the build installs |
| `example/src/main/resources/application.yaml` | Base configuration, with the datasource deliberately absent |
| `example/src/main/resources/application-local.yaml` | The local profile |
| `example/src/test/resources/application-test.yaml` | The test profile |
| `example/.github/workflows/ci.yml` | `./gradlew check` on every push |

The Gradle wrapper is committed in `example/` (`gradlew`, `gradlew.bat`,
`gradle/wrapper/`), so a copy runs without a Gradle installation.
