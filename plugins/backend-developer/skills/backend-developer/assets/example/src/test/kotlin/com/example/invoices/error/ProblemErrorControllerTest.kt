package com.example.invoices.error

import jakarta.servlet.RequestDispatcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.boot.webmvc.error.DefaultErrorAttributes
import org.springframework.mock.web.MockHttpServletRequest

/**
 * The container's error dispatch, which no @ExceptionHandler can intercept: a failure in a servlet
 * filter, or a response committed by the container itself. Without this controller those replies
 * would come back in Boot's own JSON shape instead of problem+json.
 */
class ProblemErrorControllerTest {
    private val controller = ProblemErrorController(ProblemDetailFactory(), DefaultErrorAttributes())

    private fun dispatch(
        status: Int?,
        path: String = "/api/v1/invoices",
    ) = MockHttpServletRequest("GET", "/error").apply {
        setAttribute(RequestId.ATTRIBUTE, "trace-9")
        status?.let { setAttribute(RequestDispatcher.ERROR_STATUS_CODE, it) }
        setAttribute(RequestDispatcher.ERROR_REQUEST_URI, path)
    }

    @ParameterizedTest
    @CsvSource(
        "404, urn:problem:invoices:endpoint-not-found",
        "401, urn:problem:invoices:unauthenticated",
        "403, urn:problem:invoices:access-denied",
        "405, urn:problem:invoices:method-not-allowed",
        "415, urn:problem:invoices:unsupported-media-type",
        "413, urn:problem:invoices:payload-too-large",
        "400, urn:problem:invoices:malformed-request",
        "418, urn:problem:invoices:malformed-request",
    )
    fun `a client-side error dispatch keeps the status and maps to a known type`(
        status: Int,
        expectedType: String,
    ) {
        val problem = controller.handleError(dispatch(status))

        assertThat(problem.status).isEqualTo(status)
        assertThat(problem.type.toString()).isEqualTo(expectedType)
        assertThat(problem.properties).containsEntry("traceId", "trace-9")
    }

    @Test
    fun `a server-side error dispatch says nothing about the cause`() {
        val problem = controller.handleError(dispatch(500))

        assertThat(problem.status).isEqualTo(500)
        assertThat(problem.type).isEqualTo(ErrorCode.INTERNAL_ERROR.type)
        assertThat(problem.detail).isEqualTo(ProblemDetailFactory.SAFE_SERVER_DETAIL)
    }

    @Test
    fun `an absent or nonsensical status falls back to 500`() {
        assertThat(controller.handleError(dispatch(null)).status).isEqualTo(500)
        assertThat(controller.handleError(dispatch(7)).status).isEqualTo(500)
    }

    @Test
    fun `a status with no HttpStatus constant is still reported as the container set it`() {
        // 599 has no constant. The container already chose it, so rewriting it to 500 would be
        // this service lying about what happened; only the `type` is ours to decide.
        val problem = controller.handleError(dispatch(599))

        assertThat(problem.status).isEqualTo(599)
        assertThat(problem.type).isEqualTo(ErrorCode.INTERNAL_ERROR.type)
        assertThat(problem.detail).isEqualTo(ProblemDetailFactory.SAFE_SERVER_DETAIL)
    }

    @Test
    fun `a 503 from the container is still reported as a server error`() {
        val problem = controller.handleError(dispatch(503))

        assertThat(problem.status).isEqualTo(503)
        assertThat(problem.detail).isEqualTo(ProblemDetailFactory.SAFE_SERVER_DETAIL)
    }
}
