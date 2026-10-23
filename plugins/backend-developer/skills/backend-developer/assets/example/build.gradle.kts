import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        // -Xjsr305=strict makes Spring's @Nullable/@NonNull annotations binding on Kotlin types,
        // so a platform type cannot silently become non-null.
        freeCompilerArgs.addAll("-Xjsr305=strict")
        allWarningsAsErrors = true
    }
}

// kotlin("plugin.jpa") adds the no-arg constructor Hibernate needs. It does NOT make the classes
// open, and Hibernate requires non-final classes to build lazy proxies - hence allopen below.
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

repositories {
    mavenCentral()
}

dependencies {
    // Gradle's native BOM support rather than io.spring.dependency-management: fewer plugins and
    // faster builds. Every Boot-managed dependency below is therefore declared without a version.
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation(libs.springdoc.openapi.webmvc)
    // spring-boot-starter-flyway, NOT a bare org.flywaydb:flyway-core. Boot 4 split
    // autoconfiguration into per-feature modules: with only flyway-core on the classpath the
    // library is present, no migration ever runs, and the first symptom is Hibernate's
    // `ddl-auto: validate` reporting a missing table.
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Starts compose.yaml on bootRun and wires the datasource to it. developmentOnly, so it is
    // never on the production classpath.
    //
    // The platform has to be declared again here: `developmentOnly` is a standalone configuration
    // that does not extend `implementation`, so the BOM's managed versions do not reach it. Without
    // this line the dependency resolves with an empty version and only `bootJar` fails - compile
    // and test succeed, so it is easy to commit broken.
    developmentOnly(platform(SpringBootPlugin.BOM_COORDINATES))
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    detektPlugins(libs.detekt.formatting)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    // RestTestClient's autoconfiguration is not part of spring-boot-starter-test in Boot 4.
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation(libs.mockk)
    testImplementation(libs.wiremock.standalone)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

ktlint {
    version = libs.versions.ktlintTool
    // ktlint's official code style. Rules are configured in .editorconfig, not here.
    filter {
        exclude { it.file.path.contains("/build/") }
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("detekt.yml"))
    basePath = projectDir.absolutePath
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "21"
    reports {
        html.required = true
        sarif.required = true
        txt.required = false
        md.required = false
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Testcontainers reuses one container across classes; parallel forks would start several.
    maxParallelForks = 1
    testLogging {
        events("passed", "skipped", "failed")
    }
}

kover {
    reports {
        filters {
            excludes {
                // Excluded deliberately, and the list is kept short on purpose:
                //  - the bootstrap class has no behaviour
                //  - @Configuration classes are wiring, exercised by the integration tests booting
                //  - DTO/entity classes are data holders whose generated copy/equals/hashCode/
                //    componentN members would otherwise pad the numerator with untested synthetics
                classes(
                    "com.example.invoices.ApplicationKt",
                    "com.example.invoices.Application",
                    "com.example.invoices.config.*",
                    "com.example.invoices.dto.*Request",
                    "com.example.invoices.dto.*Response",
                    "com.example.invoices.repository.*Entity",
                )
            }
        }
        verify {
            rule("line coverage") {
                bound {
                    minValue = 90
                    coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE
                }
            }
            rule("branch coverage") {
                bound {
                    minValue = 80
                    coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH
                }
            }
        }
    }
}

// `./gradlew check` is the single gate: format, static analysis, tests and coverage.
tasks.named("check") {
    dependsOn(tasks.named("koverVerify"))
}

// Installs the repo's git hooks on first build so the pre-commit gate cannot be forgotten.
//
// Both inputs are resolved here rather than inside the task: a lambda that touches `layout` or
// `project` captures a reference to the build script, which the configuration cache cannot
// serialise. `onlyIf` has the same problem, so the check becomes a plain `enabled` flag.
val gitHooksSource = layout.projectDirectory.dir("githooks")
val gitHooksTarget = layout.projectDirectory.dir(".git/hooks")
val insideGitWorkTree =
    layout.projectDirectory
        .dir(".git")
        .asFile.isDirectory

val installGitHooks =
    tasks.register<Copy>("installGitHooks") {
        from(gitHooksSource)
        into(gitHooksTarget)
        filePermissions { unix("rwxr-xr-x") }
        enabled = insideGitWorkTree
    }

tasks.named("build") { dependsOn(installGitHooks) }
