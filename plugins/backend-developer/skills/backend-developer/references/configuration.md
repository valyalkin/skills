# Configuration

One `application.yaml`, profile overlays beside it, and **typed properties everywhere**. No
`.properties` files, no `@Value`.

## Typed and validated

```kotlin
@Validated
@ConfigurationProperties(prefix = "invoices")
data class InvoiceProperties(
    @field:Valid val auth: Auth,
    @field:Valid val paymentGateway: PaymentGateway,
) {
    data class Auth(
        @field:NotBlank val issuer: String,
        @field:NotBlank val audience: String,
        @field:NotBlank val jwkSetUri: String,
    )

    data class PaymentGateway(
        @field:NotBlank val baseUrl: String,
        @field:NotNull val connectTimeout: Duration,
        @field:NotNull val readTimeout: Duration,
    )
}
```

Enabled by `@ConfigurationPropertiesScan` on the application class. Constructor binding makes the
values immutable; `@Validated` plus `@field:Valid` on the nested types means a missing or malformed
setting **fails the context at startup**, naming the property path — rather than producing a
`NullPointerException` on the first request that happens to need it. That is what turns a bad
ConfigMap into a failed rollout instead of a silent outage.

`@Value` is banned: it scatters configuration across the codebase, cannot be validated as a group,
and fails late. One properties class per concern, injected where needed.

**No hidden defaults for operational values.** The timeouts above have no Kotlin default: a
timeout that is implicit in code is a timeout nobody reviews. They are declared in
`application.yaml` where an operator can see and change them.

Relaxed binding means `jwkSetUri` ← `jwk-set-uri` ← `INVOICES_AUTH_JWKSETURI`. Kebab-case in YAML,
always.

## What is deliberately absent

There is **no `spring.datasource` block**. The connection details come from:

| Environment | Source |
| --- | --- |
| Local | `spring-boot-docker-compose` reads `compose.yaml`, including the random host port |
| Tests | the Testcontainers `@ServiceConnection` |
| Production | the ConfigMap/Secret, as `SPRING_DATASOURCE_*` |

A default in `application.yaml` would silently win over the compose-provided one and point the
application at the wrong database. Absence is the correct configuration.

## Profiles

- `application.yaml` — everything shared, with `${ENV_VAR:default}` for anything environment-shaped.
- `application-local.yaml` — `show-sql`, debug logging, docker-compose lifecycle, a local issuer.
- `application-test.yaml` (test resources) — applied by `@ActiveProfiles("test")`; the rest is
  injected by `@DynamicPropertySource` from the containers.

No `prod` profile. Production is the base file plus environment variables, so the deployed
configuration is the default path rather than a branch that only runs in production.

## Kubernetes

Values arrive as environment variables projected from a ConfigMap. Spring's relaxed binding maps
`SPRING_DATASOURCE_URL` → `spring.datasource.url` and `INVOICES_AUTH_AUDIENCE` →
`invoices.auth.audience`, so no code changes between local and cluster.

For file-mounted ConfigMaps, use a config tree instead of parsing files:

```
SPRING_CONFIG_IMPORT=optional:configtree:/etc/config/
```

Each file under `/etc/config/` becomes a property named after its path. Mount secrets separately
(`optional:configtree:/etc/secrets/`) from a `Secret`, never a ConfigMap.

Rules:

- **Secrets never go in a ConfigMap, a profile file, or the repo.** The datasource password and any
  client secret come from a `Secret`.
- A ConfigMap change does not restart pods. Either roll the deployment, or treat the value as
  immutable for the pod's lifetime — do not write code that assumes reloading.
- Keep the image identical across environments. Only the ConfigMap, the Secret and the replica
  count differ.

## Probes and actuator

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      probes:
        enabled: true
      show-details: never
```

`/actuator/health/liveness` for the liveness probe, `/actuator/health/readiness` for readiness.
Readiness includes the datasource, so a pod with a broken database leaves the load-balancer rotation
instead of serving 500s.

- `show-details: never` — health detail names internal hosts and schemas.
- Expose an explicit list. `include: "*"` publishes `env`, `beans`, `configprops` and `heapdump`.
- `server.shutdown: graceful` so in-flight requests finish during a rollout.
- `server.error.whitelabel.enabled: false` — nothing should reach it (see `errors.md`).

## Local dependencies

`compose.yaml` plus the `spring-boot-docker-compose` dev dependency: `./gradlew bootRun
--args='--spring.profiles.active=local'` starts Postgres, waits for its health check, injects the
connection details and stops it on exit. Nothing to remember, nothing left running.

Two choices keep it from colliding with any other service on the machine, and both matter:

```yaml
name: invoices-local      # scopes the compose project, so containers never clash by name
services:
  postgres:
    ports:
      - "5432"            # no host port: Docker picks a free one, Boot reads the mapping
```

Never write `"5432:5432"`. That one line is what makes two services unable to run side by side.
