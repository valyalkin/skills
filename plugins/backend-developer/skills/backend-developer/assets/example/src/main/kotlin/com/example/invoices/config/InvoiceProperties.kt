package com.example.invoices.config

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

/**
 * Typed, validated configuration. There is no @Value anywhere in this service.
 *
 * Constructor binding makes the values immutable, and @Validated means a missing or malformed
 * setting fails the context at startup with the property path in the message - rather than a
 * NullPointerException on the first request that happens to need it. That is what turns a bad
 * ConfigMap into a failed rollout instead of a silent outage.
 */
@Validated
@ConfigurationProperties(prefix = "invoices")
data class InvoiceProperties(
    @field:Valid val auth: Auth,
    @field:Valid val paymentGateway: PaymentGateway,
) {
    data class Auth(
        /** Expected `iss` claim, with the trailing slash Auth0 includes. */
        @field:NotBlank val issuer: String,
        /** Expected `aud` claim: this API's identifier in Auth0. */
        @field:NotBlank val audience: String,
        /** Auth0's JWKS endpoint. Fetched lazily on the first token, never at startup. */
        @field:NotBlank val jwkSetUri: String,
    )

    data class PaymentGateway(
        @field:NotBlank val baseUrl: String,
        // No defaults on purpose. A timeout that is implicit in code is a timeout nobody reviews;
        // these are declared in application.yaml, where an operator can see and change them, and
        // a missing value fails startup rather than silently falling back.
        @field:NotNull val connectTimeout: Duration,
        @field:NotNull val readTimeout: Duration,
    )
}
