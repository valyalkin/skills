package com.example.invoices.config

import com.example.invoices.support.AbstractIntegrationTest
import com.example.invoices.support.JwtTestTokens
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.util.UUID

/**
 * The resource-server contract, exercised against the real NimbusJwtDecoder with WireMock serving
 * the JWKS. Nothing is mocked out, so these tests would catch a decoder misconfiguration - most
 * importantly the audience check, which Spring does not perform by default.
 */
class SecurityIT : AbstractIntegrationTest() {
    private fun getInvoices(token: String?) =
        client
            .get()
            .uri("/api/v1/invoices")
            .apply { token?.let { header(HttpHeaders.AUTHORIZATION, "Bearer $it") } }
            .exchange()

    @Test
    fun `no token is a 401 problem, not an empty body`() {
        getInvoices(null)
            .expectStatus()
            .isUnauthorized()
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            // Written by the entry point rather than by a message converter, so the charset is
            // this service's responsibility: a servlet response otherwise defaults to ISO-8859-1.
            .expectHeader()
            .value(HttpHeaders.CONTENT_TYPE) { assertThat(it).containsIgnoringCase("charset=utf-8") }
            .expectBody()
            .jsonPath("$.type")
            .isEqualTo("urn:problem:invoices:unauthenticated")
            .jsonPath("$.status")
            .isEqualTo(401)
    }

    @Test
    fun `an authentication failure is as traceable as any other error`() {
        // The regression this guards against: with the request-id filter ordered at or after
        // Spring Security's chain, it never runs for a rejected request, and every 401 comes back
        // with traceId "unknown" and no X-Request-Id header - on exactly the requests somebody
        // will be trying to debug.
        getInvoices(null)
            .expectStatus()
            .isUnauthorized()
            .expectHeader()
            .exists("X-Request-Id")
            .expectBody()
            .jsonPath("$.traceId")
            .value<String> { assertThat(it).isNotBlank().isNotEqualTo("unknown") }
    }

    @Test
    fun `an inbound request id survives an authentication failure`() {
        client
            .get()
            .uri("/api/v1/invoices")
            .header("X-Request-Id", "caller-trace-7")
            .exchange()
            .expectStatus()
            .isUnauthorized()
            .expectHeader()
            .valueEquals("X-Request-Id", "caller-trace-7")
            .expectBody()
            .jsonPath("$.traceId")
            .isEqualTo("caller-trace-7")
    }

    @Test
    fun `a token for another API's audience is rejected`() {
        getInvoices(JwtTestTokens.wrongAudienceToken())
            .expectStatus()
            .isUnauthorized()
            .expectBody()
            .jsonPath("$.type")
            .isEqualTo("urn:problem:invoices:unauthenticated")
    }

    @Test
    fun `an expired token is rejected`() {
        getInvoices(JwtTestTokens.expiredToken()).expectStatus().isUnauthorized()
    }

    @Test
    fun `a token from another issuer is rejected`() {
        getInvoices(JwtTestTokens.token(issuer = "https://evil.example.com/"))
            .expectStatus()
            .isUnauthorized()
    }

    @Test
    fun `a token that is not a JWT at all is rejected`() {
        getInvoices("not-a-jwt").expectStatus().isUnauthorized()
    }

    @Test
    fun `a valid token is accepted`() {
        getInvoices(JwtTestTokens.token()).expectStatus().isOk()
    }

    @Test
    fun `a token without the write scope cannot create, and gets a 403 problem`() {
        client
            .post()
            .uri("/api/v1/invoices")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${JwtTestTokens.readOnlyToken()}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"customerId":"${UUID.randomUUID()}","amountCents":2500}""")
            .exchange()
            .expectStatus()
            .isForbidden()
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.type")
            .isEqualTo("urn:problem:invoices:access-denied")
    }

    @Test
    fun `the probes and the API document stay reachable without a token`() {
        client
            .get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus()
            .isOk()
        client
            .get()
            .uri("/actuator/health/readiness")
            .exchange()
            .expectStatus()
            .isOk()
        client
            .get()
            .uri("/v3/api-docs")
            .exchange()
            .expectStatus()
            .isOk()
    }
}
