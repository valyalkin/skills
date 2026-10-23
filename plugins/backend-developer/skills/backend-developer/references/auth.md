# Auth0 as an OAuth2 resource server

The frontend owns the user session (see the `frontend-developer` skill: the Auth0 session lives in
a server-side cookie and the browser never holds a token). **This service validates a bearer token
on every request and trusts nothing else** — no session, no cookie, no header the caller could set.

## Configuration

```yaml
invoices:
  auth:
    issuer: ${AUTH0_ISSUER:https://example.eu.auth0.com/}
    audience: ${AUTH0_AUDIENCE:https://api.example.com}
    jwk-set-uri: ${AUTH0_JWK_SET_URI:https://example.eu.auth0.com/.well-known/jwks.json}
```

Typed into `@ConfigurationProperties`, not `spring.security.oauth2.resourceserver.*`, for a
specific reason: `issuer-uri` makes Spring resolve the OIDC metadata document **eagerly during
startup**, so the service cannot boot while Auth0 is unreachable. `withJwkSetUri` fetches on first
use and caches.

```kotlin
@Bean
fun jwtDecoder(): JwtDecoder =
    NimbusJwtDecoder.withJwkSetUri(properties.auth.jwkSetUri).build()
        .apply { setJwtValidator(tokenValidator()) }

private fun tokenValidator(): OAuth2TokenValidator<Jwt> =
    DelegatingOAuth2TokenValidator(
        JwtValidators.createDefaultWithIssuer(properties.auth.issuer),  // issuer + exp/nbf
        JwtClaimValidator<List<String>>(JwtClaimNames.AUD, ::hasExpectedAudience),
    )
```

**Spring's default validator does not check `aud`.** Without the second validator this service
accepts any token the tenant issued for any other API — the single most common resource-server
misconfiguration. A test asserts a wrong-audience token is rejected.

A Kotlin detail with teeth: `Predicate<T>` is a Java type, so Kotlin types the claim as non-null,
but a token minted for another API has no `aud` claim and Spring passes `null`. The predicate must
accept a nullable list, or the rejection path throws an NPE and surfaces as a 500 instead of a 401.

## Authorization

Scopes map to `SCOPE_*` authorities. Authorize **on the service**, not the controller:

```kotlin
@PreAuthorize("hasAuthority('SCOPE_invoices:write')")
@Transactional
fun create(command: CreateInvoice): Invoice
```

The rule then holds for every caller of that method, including a future second controller or a
scheduled job, and it sits next to the logic it protects. `@EnableMethodSecurity` is required.

Stateless, so: `SessionCreationPolicy.STATELESS` and CSRF disabled — there is no session for CSRF
to protect and leaving it on breaks non-browser clients.

## 401 and 403 must be problem+json too

These are produced in the filter chain, **before** the dispatcher, so no `@ExceptionHandler` can
see them. Left alone they come back with an empty body — violating the "every error is a
ProblemDetail" rule precisely where a caller most needs a diagnosis.

Wire **both** entry points. This is the part that is almost always missed:

```kotlin
.oauth2ResourceServer { oauth2 ->
    oauth2.jwt(Customizer.withDefaults())
    // Invalid / expired / wrong-audience tokens are rejected inside
    // BearerTokenAuthenticationFilter, which uses the resource server's OWN entry point.
    oauth2.authenticationEntryPoint { request, response, _ -> writeProblem(request, response, UNAUTHENTICATED) }
    oauth2.accessDeniedHandler  { request, response, _ -> writeProblem(request, response, ACCESS_DENIED) }
}
.exceptionHandling { handling ->
    // Reached when there is no token at all.
    handling.authenticationEntryPoint { ... }.accessDeniedHandler { ... }
}
```

Configure only `exceptionHandling` and you get a well-formed problem for a *missing* token and an
empty body for an *invalid* one: same status, two different shapes.

When writing the problem by hand, set the charset explicitly:

```kotlin
response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
response.characterEncoding = StandardCharsets.UTF_8.name()   // servlet default is ISO-8859-1
```

This is the one error path that bypasses Spring's message converters, so it is the one path that
will otherwise answer in a different charset from every other and mangle non-ASCII detail.

`AccessDeniedException` thrown by method security *does* reach the advice. There it maps to 401 if
the authentication is absent or anonymous and 403 otherwise.

### Order the request-id filter before the security chain

Spring Security's chain is registered at order **-100** (`spring.security.filter.order`). A filter
at the same order or later never runs for a rejected request, so every 401 and 403 comes back with
`traceId: "unknown"` and no `X-Request-Id` header — on exactly the requests somebody is trying to
debug. Register at `-110`. Two tests in the example fail if that number is changed back.

## Public paths

`/actuator/health`, `/actuator/health/**`, `/actuator/info`, `/v3/api-docs`, `/v3/api-docs.yaml`,
`/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`. Everything else is `authenticated()`.
Never expose `/actuator/**` wholesale — that includes `env`, `beans` and `configprops`.

## Testing

Mint **real signed tokens** and serve the matching JWKS from WireMock
(`support/JwtTestTokens.kt`): generate an RSA key, publish the public JWK at the stubbed
`jwk-set-uri`, and sign JWTs with configurable issuer, audience, scopes and expiry. The production
`NimbusJwtDecoder`, the real signature check and the real validators are all exercised.

Replacing the `JwtDecoder` with a mock, by contrast, proves only that the mock works — it would
pass with the audience check deleted.

`SecurityIT` covers: no token (401), wrong audience (401), expired (401), wrong issuer (401),
non-JWT (401), valid (200), valid-but-insufficient-scope (403), public paths without a token, and
the traceId/`X-Request-Id` behaviour on a rejected request. Never point a test at a tenant holding
real users.
