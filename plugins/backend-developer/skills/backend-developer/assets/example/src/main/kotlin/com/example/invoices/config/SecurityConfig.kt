package com.example.invoices.config

import com.example.invoices.error.ErrorCode
import com.example.invoices.error.ProblemDetailFactory
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimNames
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain
import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets

/**
 * Auth0 as an OAuth2 resource server.
 *
 * Three things here are the ones usually got wrong:
 *
 *  1. **the audience is validated explicitly.** Spring's default validator checks the issuer and
 *     the timestamps, not `aud`. Without the check below, a token Auth0 minted for any other API
 *     in the same tenant is accepted by this service.
 *  2. **the decoder is built from a JWKS URI, not an issuer URI.** `issuer-uri` resolves the OIDC
 *     metadata document eagerly during startup, which makes booting this service depend on Auth0
 *     being reachable. `withJwkSetUri` fetches on first use and caches.
 *  3. **401 and 403 are problem+json too.** Those are produced in the filter chain, before the
 *     dispatcher, so no @ExceptionHandler can see them; left alone they come back with an empty
 *     body. Both entry points below delegate to the same [ProblemDetailFactory] the advice uses.
 */
@Configuration
@EnableMethodSecurity
class SecurityConfig(
    private val properties: InvoiceProperties,
    private val problems: ProblemDetailFactory,
    private val objectMapper: ObjectMapper,
) {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { authorize ->
                authorize
                    .requestMatchers(*PUBLIC_PATHS)
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }.oauth2ResourceServer { oauth2 ->
                oauth2.jwt(Customizer.withDefaults())
                // Both entry points are needed, and this is the one usually missed. A request with
                // an invalid, expired or wrong-audience token is rejected inside
                // BearerTokenAuthenticationFilter, which uses the resource server's own entry
                // point - not the one configured on exceptionHandling below. Without these two
                // lines such a request comes back as a 401 with an empty body, while a request
                // with no token at all gets a proper problem response: the same status, two
                // different shapes.
                oauth2.authenticationEntryPoint { request, response, _ ->
                    writeProblem(request, response, ErrorCode.UNAUTHENTICATED)
                }
                oauth2.accessDeniedHandler { request, response, _ ->
                    writeProblem(request, response, ErrorCode.ACCESS_DENIED)
                }
            }
            // Stateless: the caller presents a token on every request and this service keeps no
            // session. CSRF protection is meaningless without one and would break API clients.
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .csrf { it.disable() }
            .exceptionHandling { handling ->
                handling
                    .authenticationEntryPoint { request, response, _ ->
                        writeProblem(request, response, ErrorCode.UNAUTHENTICATED)
                    }.accessDeniedHandler { request, response, _ ->
                        writeProblem(request, response, ErrorCode.ACCESS_DENIED)
                    }
            }
        return http.build()
    }

    @Bean
    fun jwtDecoder(): JwtDecoder =
        NimbusJwtDecoder
            .withJwkSetUri(properties.auth.jwkSetUri)
            .build()
            .apply { setJwtValidator(tokenValidator()) }

    private fun tokenValidator(): OAuth2TokenValidator<Jwt> =
        DelegatingOAuth2TokenValidator(
            // Issuer + `exp`/`nbf`.
            JwtValidators.createDefaultWithIssuer(properties.auth.issuer),
            // The check Spring does not do for you.
            JwtClaimValidator<List<String>>(JwtClaimNames.AUD, ::hasExpectedAudience),
        )

    /**
     * Kotlin sees the claim as non-null because Predicate<T> is a Java type, but a token minted
     * for a different API has no `aud` claim at all and Spring passes null. Taking a nullable
     * parameter here is what keeps that case a clean 401 rather than a NullPointerException
     * surfacing as a 500.
     */
    private fun hasExpectedAudience(audiences: List<String>?): Boolean =
        audiences?.contains(properties.auth.audience) == true

    private fun writeProblem(
        request: HttpServletRequest,
        response: HttpServletResponse,
        errorCode: ErrorCode,
    ) {
        val problem = problems.create(errorCode, errorCode.title, request)
        response.status = errorCode.status.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        // Explicit, and before the writer is taken: a servlet response defaults to ISO-8859-1, so
        // without this the one error path that does not go through Spring's message converters
        // would answer in a different charset from every other - and mangle any non-ASCII detail.
        response.characterEncoding = StandardCharsets.UTF_8.name()
        objectMapper.writeValue(response.writer, problem)
    }

    private companion object {
        val PUBLIC_PATHS =
            arrayOf(
                "/actuator/health",
                "/actuator/health/**",
                "/actuator/info",
                "/v3/api-docs",
                // springdoc serves the YAML form at a suffixed path, which "/v3/api-docs/**"
                // does not match.
                "/v3/api-docs.yaml",
                "/v3/api-docs/**",
                "/swagger-ui/**",
                "/swagger-ui.html",
            )
    }
}
