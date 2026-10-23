package com.example.invoices.error

import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validation
import jakarta.validation.constraints.NotBlank
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.mock.http.MockHttpInputMessage
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.servlet.resource.NoResourceFoundException
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

/**
 * Handler-level coverage of every mapping, including the ones that are awkward to provoke over
 * real HTTP (an optimistic lock, an oversized upload). The wire-level behaviour is asserted
 * separately in GlobalExceptionHandlerIT.
 */
class GlobalExceptionHandlerTest {
    private val handler = GlobalExceptionHandler(ProblemDetailFactory(), ViolationExtractor())
    private val request = MockHttpServletRequest("POST", "/api/v1/invoices")
    private val mapper: ObjectMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    @AfterEach
    fun clearContext() = SecurityContextHolder.clearContext()

    private fun ProblemDetail.errors(): List<*> = properties?.get("errors") as? List<*> ?: emptyList<Any>()

    @Test
    fun `a not found becomes a 404 carrying the code's type and the thrown message`() {
        val problem =
            handler.handleNotFound(
                NotFoundException(ErrorCode.INVOICE_NOT_FOUND, "Invoice 42 was not found"),
                request,
            )

        assertThat(problem.status).isEqualTo(404)
        assertThat(problem.type).isEqualTo(ErrorCode.INVOICE_NOT_FOUND.type)
        assertThat(problem.detail).isEqualTo("Invoice 42 was not found")
    }

    @Test
    fun `a duplicate becomes a 409 and an unprocessable entity a 422`() {
        assertThat(
            handler
                .handleDuplicate(DuplicateException(ErrorCode.DUPLICATE_INVOICE, "exists"), request)
                .status,
        ).isEqualTo(409)

        assertThat(
            handler
                .handleUnprocessable(
                    UnprocessableEntityException(ErrorCode.INVOICE_NOT_PAYABLE, "already paid"),
                    request,
                ).status,
        ).isEqualTo(422)
    }

    @Test
    fun `the base business case is a 400 and forwards its violations`() {
        val violation = FieldViolation("#/amount", "amount", "Min", "must be greater than 0")

        val problem =
            handler.handleBusiness(
                BusinessException(ErrorCode.VALIDATION_FAILED, "bad", listOf(violation)),
                request,
            )

        assertThat(problem.status).isEqualTo(400)
        assertThat(problem.errors()).containsExactly(violation)
    }

    @Test
    fun `a system exception reports its status but never its message`() {
        val problem =
            handler.handleSystem(
                SystemException("connection refused to payments-db:5432", ErrorCode.UPSTREAM_UNAVAILABLE),
                request,
            )

        assertThat(problem.status).isEqualTo(503)
        assertThat(problem.detail).isEqualTo(ProblemDetailFactory.SAFE_SERVER_DETAIL)
        assertThat(problem.detail).doesNotContain("payments-db", "5432")
    }

    @Test
    fun `an unexpected exception is a 500 that leaks nothing`() {
        val problem =
            handler.handleUnexpected(
                IllegalStateException("table invoices column amount_cents does not exist"),
                request,
            )

        assertThat(problem.status).isEqualTo(500)
        assertThat(problem.detail).isEqualTo(ProblemDetailFactory.SAFE_SERVER_DETAIL)
        assertThat(problem.detail).doesNotContain("invoices", "amount_cents", "IllegalState")
    }

    @Test
    fun `a constraint violation is reported field by field`() {
        data class Payload(
            @field:NotBlank val reference: String,
        )

        val validator = Validation.buildDefaultValidatorFactory().validator
        val exception = ConstraintViolationException(validator.validate(Payload("")))

        val problem = handler.handleConstraintViolation(exception, request)

        assertThat(problem.status).isEqualTo(400)
        assertThat(problem.detail).isEqualTo("1 field is invalid: reference")
        assertThat(problem.errors()).singleElement().isEqualTo(
            FieldViolation("#/reference", "reference", "NotBlank", "must not be blank"),
        )
    }

    @Test
    fun `an unparseable body is a 400 that does not echo the parser message`() {
        val parseFailure =
            runCatching { mapper.readValue("{", Map::class.java) }.exceptionOrNull()
        val exception =
            HttpMessageNotReadableException(
                "JSON parse error: unexpected end of input at [Source: (String)\"{\"; line: 1]",
                parseFailure,
                MockHttpInputMessage("{".toByteArray()),
            )

        val problem = handler.handleUnreadableBody(exception, request)

        assertThat(problem.status).isEqualTo(400)
        assertThat(problem.type).isEqualTo(ErrorCode.MALFORMED_REQUEST.type)
        assertThat(problem.detail).isEqualTo("The request body is not valid JSON.")
        assertThat(problem.detail).doesNotContain("Source", "line: 1")
    }

    @Test
    fun `a data integrity violation is a 409 and the constraint name stays in the log`() {
        val problem =
            handler.handleDataIntegrity(
                DataIntegrityViolationException("ux_invoices_customer_amount_pending violated"),
                request,
            )

        assertThat(problem.status).isEqualTo(409)
        assertThat(problem.detail).doesNotContain("ux_invoices_customer_amount_pending")
    }

    @Test
    fun `an optimistic lock failure asks the caller to retry`() {
        val problem =
            handler.handleOptimisticLocking(OptimisticLockingFailureException("row changed"), request)

        assertThat(problem.status).isEqualTo(409)
        assertThat(problem.type).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION.type)
        assertThat(problem.detail).contains("retry")
    }

    @Test
    fun `access denied is a 401 when nobody is authenticated and a 403 when somebody is`() {
        SecurityContextHolder.getContext().authentication =
            AnonymousAuthenticationToken("key", "anonymous", listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS")))

        assertThat(handler.handleAccessDenied(AccessDeniedException("denied"), request).status)
            .isEqualTo(401)

        SecurityContextHolder.getContext().authentication =
            TestingAuthenticationToken("user", "token", "SCOPE_invoices:read")

        assertThat(handler.handleAccessDenied(AccessDeniedException("denied"), request).status)
            .isEqualTo(403)
    }

    @Test
    fun `an unknown path is a 404 naming the method and path`() {
        val problem =
            handler.handleNoResource(
                // Framework 7 takes (method, requestPath, resourcePath).
                NoResourceFoundException(HttpMethod.GET, "/api/v1/nope", "nope"),
                MockHttpServletRequest("GET", "/api/v1/nope"),
            )

        assertThat(problem.status).isEqualTo(404)
        assertThat(problem.type).isEqualTo(ErrorCode.ENDPOINT_NOT_FOUND.type)
        assertThat(problem.detail).contains("GET", "/api/v1/nope")
    }

    @Test
    fun `an unsupported method lists what is supported`() {
        val problem =
            handler.handleMethodNotSupported(
                HttpRequestMethodNotSupportedException("DELETE", listOf("GET", "POST")),
                request,
            )

        assertThat(problem.status).isEqualTo(405)
        assertThat(problem.detail).contains("DELETE", "GET", "POST")
    }

    @Test
    fun `an unsupported content type says what to send instead`() {
        val problem =
            handler.handleMediaTypeNotSupported(
                HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, listOf(MediaType.APPLICATION_JSON)),
                request,
            )

        assertThat(problem.status).isEqualTo(415)
        assertThat(problem.detail).contains("application/json")
    }

    @Test
    fun `an unacceptable Accept header is a 406`() {
        val problem =
            handler.handleNotAcceptable(
                HttpMediaTypeNotAcceptableException(listOf(MediaType.APPLICATION_JSON)),
                request,
            )

        assertThat(problem.status).isEqualTo(406)
    }

    @Test
    fun `a validation failure with no extractable violations still reads sensibly`() {
        // The 0-violation branch of the detail builder: reachable when a framework exception
        // carries nothing this service can attribute to a field.
        val exception = ConstraintViolationException(emptySet())

        val problem = handler.handleConstraintViolation(exception, request)

        assertThat(problem.status).isEqualTo(400)
        assertThat(problem.detail).isEqualTo("The request is invalid.")
        assertThat(problem.errors()).isEmpty()
    }

    @Test
    fun `an unsupported method with no supported list does not render a null`() {
        val problem =
            handler.handleMethodNotSupported(
                HttpRequestMethodNotSupportedException("TRACE"),
                request,
            )

        assertThat(problem.status).isEqualTo(405)
        assertThat(problem.detail).doesNotContain("null")
    }

    @Test
    fun `an oversized body is a 413`() {
        val problem = handler.handleUploadTooLarge(MaxUploadSizeExceededException(1024), request)

        assertThat(problem.status).isEqualTo(413)
        assertThat(problem.type).isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE.type)
    }
}
