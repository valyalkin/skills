package com.example.invoices.error

import com.example.failures.FailureController
import com.example.invoices.model.InvoiceStatus
import com.example.invoices.repository.InvoiceEntity
import com.example.invoices.support.AbstractIntegrationTest
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.serverError
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import java.time.Instant
import java.util.UUID

/**
 * The load-bearing test of this service: one real request per error shape, asserting that every
 * single one answers with problem+json, the right status, a stable `type`, a traceId, and - where
 * the request was at fault - an `errors` entry that names the field.
 *
 * It is what turns "every error is an RFC 9457 problem" from a convention into a property of the
 * build. If a future change lets any endpoint answer with a different shape, this fails.
 */
class GlobalExceptionHandlerIT : AbstractIntegrationTest() {
    private val customerId = UUID.fromString("6f1c7d4e-0f4a-4f1a-9a8e-2b9d2a3c4d5e")

    /** Registers the failure endpoints; see [FailureController] for why they live where they do. */
    @TestConfiguration
    class FailureEndpoints {
        @Bean
        fun failureController(): FailureController = FailureController()
    }

    private fun seedPending(amountCents: Long = 2500): InvoiceEntity =
        invoices.save(
            InvoiceEntity(
                id = UUID.randomUUID(),
                customerId = customerId,
                amountCents = amountCents,
                status = InvoiceStatus.PENDING,
                createdAt = Instant.now(),
            ),
        )

    private fun get(uri: String) =
        client
            .get()
            .uri(uri)
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .exchange()

    private fun postJson(
        uri: String,
        body: String,
        contentType: MediaType = MediaType.APPLICATION_JSON,
    ) = client
        .post()
        .uri(uri)
        .header(HttpHeaders.AUTHORIZATION, bearer())
        .contentType(contentType)
        .body(body)
        .exchange()

    /** Every error response, whatever its status, must look like this. */
    private fun RestTestClient.ResponseSpec.expectProblem(
        status: Int,
        type: String,
    ) = expectStatus()
        .isEqualTo(status)
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectHeader()
        .exists("X-Request-Id")
        .expectBody()
        .jsonPath("$.type")
        .isEqualTo(type)
        .jsonPath("$.status")
        .isEqualTo(status)
        .jsonPath("$.title")
        .exists()
        .jsonPath("$.instance")
        .exists()
        .jsonPath("$.traceId")
        .value<String> { assertThat(it).isNotBlank().isNotEqualTo("unknown") }
        .jsonPath("$.timestamp")
        .exists()

    @Test
    fun `404 - a missing invoice names the id and nothing else`() {
        val id = UUID.randomUUID()

        get("/api/v1/invoices/$id")
            .expectProblem(404, "urn:problem:invoices:invoice-not-found")
            .jsonPath("$.detail")
            .isEqualTo("Invoice $id was not found")
    }

    @Test
    fun `404 - an unknown path is still a problem, not an HTML error page`() {
        get("/api/v1/nope").expectProblem(404, "urn:problem:invoices:endpoint-not-found")
    }

    @Test
    fun `409 - a duplicate pending invoice`() {
        invoices.save(
            InvoiceEntity(
                id = UUID.randomUUID(),
                customerId = customerId,
                amountCents = 2500,
                status = InvoiceStatus.PENDING,
                createdAt = Instant.now(),
            ),
        )

        postJson("/api/v1/invoices", """{"customerId":"$customerId","amountCents":2500}""")
            .expectProblem(409, "urn:problem:invoices:duplicate-invoice")
    }

    @Test
    fun `422 - paying a settled invoice is refused, not misreported as a 400`() {
        val paid =
            invoices.save(
                InvoiceEntity(
                    id = UUID.randomUUID(),
                    customerId = customerId,
                    amountCents = 2500,
                    status = InvoiceStatus.PAID,
                    createdAt = Instant.now(),
                ),
            )

        client
            .post()
            .uri("/api/v1/invoices/{id}/payment", paid.id)
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .exchange()
            .expectProblem(422, "urn:problem:invoices:invoice-not-payable")
    }

    @Test
    fun `400 - bean validation names every offending field with a JSON pointer`() {
        postJson("/api/v1/invoices", """{"customerId":"$customerId","amountCents":-1}""")
            .expectProblem(400, "urn:problem:invoices:validation-failed")
            .jsonPath("$.detail")
            .isEqualTo("1 field is invalid: amountCents")
            .jsonPath("$.errors[0].pointer")
            .isEqualTo("#/amountCents")
            .jsonPath("$.errors[0].field")
            .isEqualTo("amountCents")
            .jsonPath("$.errors[0].code")
            .isEqualTo("Positive")
            .jsonPath("$.errors[0].message")
            .isEqualTo("must be greater than 0")
    }

    @Test
    fun `400 - a missing body field is reported by name, not as a parse failure`() {
        postJson("/api/v1/invoices", """{"amountCents":2500}""")
            .expectProblem(400, "urn:problem:invoices:validation-failed")
            .jsonPath("$.errors[0].field")
            .isEqualTo("customerId")
            .jsonPath("$.errors[0].pointer")
            .isEqualTo("#/customerId")
            .jsonPath("$.errors[0].code")
            .isEqualTo("Required")
    }

    @Test
    fun `400 - a wrong field type names the expected type and never echoes the value`() {
        postJson("/api/v1/invoices", """{"customerId":"$customerId","amountCents":"not-a-number"}""")
            .expectProblem(400, "urn:problem:invoices:validation-failed")
            .jsonPath("$.errors[0].field")
            .isEqualTo("amountCents")
            .jsonPath("$.errors[0].code")
            .isEqualTo("TypeMismatch")
            .jsonPath("$.detail")
            .value<String> { require(!it.contains("not-a-number")) { "the rejected value leaked: $it" } }
    }

    @Test
    fun `400 - an unknown property is named rather than silently dropped`() {
        postJson(
            "/api/v1/invoices",
            """{"customerId":"$customerId","amountCents":2500,"discountPct":10}""",
        ).expectProblem(400, "urn:problem:invoices:validation-failed")
            .jsonPath("$.errors[0].field")
            .isEqualTo("discountPct")
            .jsonPath("$.errors[0].code")
            .isEqualTo("Unknown")
    }

    @Test
    fun `400 - malformed JSON does not leak the parser's message`() {
        postJson("/api/v1/invoices", "{not json at all")
            .expectProblem(400, "urn:problem:invoices:malformed-request")
            .jsonPath("$.detail")
            .isEqualTo("The request body is not valid JSON.")
    }

    @Test
    fun `400 - an unparseable path variable names the parameter`() {
        get("/api/v1/invoices/not-a-uuid")
            .expectProblem(400, "urn:problem:invoices:validation-failed")
            .jsonPath("$.errors[0].field")
            .isEqualTo("id")
            .jsonPath("$.errors[0].code")
            .isEqualTo("TypeMismatch")
            .jsonPath("$.errors[0].message")
            .isEqualTo("must be a valid UUID")
    }

    @Test
    fun `400 - a missing query parameter names it`() {
        get("/api/v1/test-failures/required-param")
            .expectProblem(400, "urn:problem:invoices:validation-failed")
            .jsonPath("$.errors[0].field")
            .isEqualTo("reference")
            .jsonPath("$.errors[0].code")
            .isEqualTo("Required")
    }

    @Test
    fun `400 - a missing header names it`() {
        get("/api/v1/test-failures/required-header")
            .expectProblem(400, "urn:problem:invoices:validation-failed")
            .jsonPath("$.errors[0].field")
            .isEqualTo("X-Tenant-Id")
            .jsonPath("$.errors[0].code")
            .isEqualTo("Required")
    }

    @Test
    fun `405 - the wrong method lists what is allowed`() {
        client
            .delete()
            .uri("/api/v1/invoices/{id}", UUID.randomUUID())
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .exchange()
            .expectProblem(405, "urn:problem:invoices:method-not-allowed")
    }

    @Test
    fun `415 - a non-JSON content type is refused`() {
        postJson("/api/v1/invoices", "customerId=1", MediaType.TEXT_PLAIN)
            .expectProblem(415, "urn:problem:invoices:unsupported-media-type")
    }

    @Test
    fun `500 - an unmapped exception is a problem that leaks nothing`() {
        get("/api/v1/test-failures/unexpected")
            .expectProblem(500, "urn:problem:invoices:internal-error")
            .jsonPath("$.detail")
            .isEqualTo(ProblemDetailFactory.SAFE_SERVER_DETAIL)
            .jsonPath("$.detail")
            .value<String> {
                require(!it.contains("amount_cents") && !it.contains("IllegalState")) {
                    "internal detail leaked: $it"
                }
            }
    }

    @Test
    fun `503 - a gateway that answers 200 with no charge id is still a failure`() {
        val pending = seedPending()
        wireMock.stubFor(post(urlPathEqualTo("/charges")).willReturn(okJson("{}")))

        client
            .post()
            .uri("/api/v1/invoices/{id}/payment", pending.id)
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .exchange()
            .expectProblem(503, "urn:problem:invoices:upstream-unavailable")

        assertThat(invoices.findById(pending.id))
            .get()
            .extracting { it.status }
            .isEqualTo(InvoiceStatus.PENDING)
    }

    @Test
    fun `503 - an upstream failure is reported as the service's own fault`() {
        val pending = seedPending()
        wireMock.stubFor(post(urlPathEqualTo("/charges")).willReturn(serverError()))

        client
            .post()
            .uri("/api/v1/invoices/{id}/payment", pending.id)
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .exchange()
            .expectProblem(503, "urn:problem:invoices:upstream-unavailable")

        // The invoice must not have been settled on a failed charge.
        assertThat(invoices.findById(pending.id))
            .get()
            .extracting { it.status }
            .isEqualTo(InvoiceStatus.PENDING)
    }
}
