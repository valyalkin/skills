package com.example.invoices.error

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import java.time.Instant

class ProblemDetailFactoryTest {
    private val factory = ProblemDetailFactory()

    private fun request(
        uri: String = "/api/v1/invoices/42",
        attributeId: String? = null,
        headerId: String? = null,
    ) = MockHttpServletRequest("GET", uri).apply {
        attributeId?.let { setAttribute(RequestId.ATTRIBUTE, it) }
        headerId?.let { addHeader(RequestId.HEADER, it) }
    }

    @Test
    fun `a problem carries the registered members plus traceId`() {
        val problem =
            factory.create(
                ErrorCode.INVOICE_NOT_FOUND,
                "Invoice 42 was not found",
                request(attributeId = "trace-1"),
            )

        assertThat(problem.status).isEqualTo(HttpStatus.NOT_FOUND.value())
        assertThat(problem.type).isEqualTo(ErrorCode.INVOICE_NOT_FOUND.type)
        assertThat(problem.title).isEqualTo("Invoice not found")
        assertThat(problem.detail).isEqualTo("Invoice 42 was not found")
        assertThat(problem.instance.toString()).isEqualTo("/api/v1/invoices/42")
        assertThat(problem.properties).containsEntry("traceId", "trace-1")
        assertThat(problem.properties?.get("timestamp")).isInstanceOf(Instant::class.java)
    }

    @Test
    fun `errors is omitted when there is nothing to report`() {
        val problem = factory.create(ErrorCode.INVOICE_NOT_FOUND, "nope", request())

        assertThat(problem.properties).doesNotContainKey("errors")
    }

    @Test
    fun `errors is present when violations are given`() {
        val violation = FieldViolation("#/amount", "amount", "Min", "must be greater than 0")

        val problem =
            factory.create(
                ErrorCode.VALIDATION_FAILED,
                "1 field is invalid: amount",
                request(),
                listOf(violation),
            )

        assertThat(problem.properties?.get("errors")).isEqualTo(listOf(violation))
    }

    @Test
    fun `the traceId falls back to the inbound header, then to unknown`() {
        assertThat(factory.create(ErrorCode.INTERNAL_ERROR, "x", request(headerId = "from-header")).properties)
            .containsEntry("traceId", "from-header")

        assertThat(factory.create(ErrorCode.INTERNAL_ERROR, "x", request()).properties)
            .containsEntry("traceId", "unknown")
    }

    @Test
    fun `the filter-supplied id wins over the inbound header`() {
        val problem =
            factory.create(
                ErrorCode.INTERNAL_ERROR,
                "x",
                request(attributeId = "generated", headerId = "inbound"),
            )

        assertThat(problem.properties).containsEntry("traceId", "generated")
    }

    @Test
    fun `a server error never describes the cause`() {
        val problem = factory.createServerError(ErrorCode.INTERNAL_ERROR, request())

        assertThat(problem.detail).isEqualTo(ProblemDetailFactory.SAFE_SERVER_DETAIL)
        assertThat(problem.detail).doesNotContain("Exception", "SQL", "com.example")
        assertThat(problem.status).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value())
    }
}
