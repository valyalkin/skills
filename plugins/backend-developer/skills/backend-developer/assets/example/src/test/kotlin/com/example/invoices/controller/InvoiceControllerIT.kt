package com.example.invoices.controller

import com.example.invoices.model.InvoiceStatus
import com.example.invoices.repository.InvoiceEntity
import com.example.invoices.support.AbstractIntegrationTest
import com.example.invoices.support.JwtTestTokens
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.time.Instant
import java.util.UUID

/** The happy paths, end to end: HTTP in, Postgres and WireMock behind, JSON out. */
class InvoiceControllerIT : AbstractIntegrationTest() {
    private val customerId = UUID.fromString("6f1c7d4e-0f4a-4f1a-9a8e-2b9d2a3c4d5e")

    private fun seed(
        status: InvoiceStatus = InvoiceStatus.PENDING,
        amountCents: Long = 2500,
    ): InvoiceEntity =
        invoices.save(
            InvoiceEntity(
                id = UUID.randomUUID(),
                customerId = customerId,
                amountCents = amountCents,
                status = status,
                createdAt = Instant.parse("2026-10-23T09:15:00Z"),
            ),
        )

    @Test
    fun `create returns 201 and persists the invoice`() {
        client
            .post()
            .uri("/api/v1/invoices")
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"customerId":"$customerId","amountCents":2500}""")
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("PENDING")
            .jsonPath("$.amountCents")
            .isEqualTo(2500)
            .jsonPath("$.createdAt")
            // RFC 3339, never epoch millis.
            .value<String> { assertThat(it).matches("""\d{4}-\d{2}-\d{2}T.*Z""") }

        assertThat(invoices.findAll()).singleElement().satisfies({
            assertThat(it.customerId).isEqualTo(customerId)
            assertThat(it.status).isEqualTo(InvoiceStatus.PENDING)
        })
    }

    @Test
    fun `get returns the invoice`() {
        val saved = seed()

        client
            .get()
            .uri("/api/v1/invoices/{id}", saved.id)
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.id")
            .isEqualTo(saved.id.toString())
    }

    @Test
    fun `list pages and filters by status`() {
        seed(amountCents = 100)
        seed(amountCents = 200)
        seed(status = InvoiceStatus.PAID, amountCents = 300)

        client
            .get()
            .uri("/api/v1/invoices?status=PENDING&page=0&size=1")
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.content.length()")
            .isEqualTo(1)
            .jsonPath("$.totalElements")
            .isEqualTo(2)
            .jsonPath("$.totalPages")
            .isEqualTo(2)
            .jsonPath("$.page")
            .isEqualTo(0)
    }

    @Test
    fun `pay charges the gateway and settles the invoice`() {
        val saved = seed()
        wireMock.stubFor(
            post(urlPathEqualTo("/charges")).willReturn(okJson("""{"chargeId":"ch_123"}""")),
        )

        client
            .post()
            .uri("/api/v1/invoices/{id}/payment", saved.id)
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("PAID")

        assertThat(invoices.findById(saved.id)).get().extracting { it.status }.isEqualTo(InvoiceStatus.PAID)
    }

    @Test
    fun `the request id is echoed so a client can correlate its own trace`() {
        client
            .get()
            .uri("/api/v1/invoices/{id}", UUID.randomUUID())
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .header("X-Request-Id", "client-supplied-id")
            .exchange()
            .expectStatus()
            .isNotFound()
            .expectHeader()
            .valueEquals("X-Request-Id", "client-supplied-id")
            .expectBody()
            .jsonPath("$.traceId")
            .isEqualTo("client-supplied-id")
    }

    @Test
    fun `a read-only token may list but not create`() {
        client
            .get()
            .uri("/api/v1/invoices")
            .header(HttpHeaders.AUTHORIZATION, bearer(JwtTestTokens.readOnlyToken()))
            .exchange()
            .expectStatus()
            .isOk()

        client
            .post()
            .uri("/api/v1/invoices")
            .header(HttpHeaders.AUTHORIZATION, bearer(JwtTestTokens.readOnlyToken()))
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"customerId":"$customerId","amountCents":2500}""")
            .exchange()
            .expectStatus()
            .isForbidden()
    }
}
