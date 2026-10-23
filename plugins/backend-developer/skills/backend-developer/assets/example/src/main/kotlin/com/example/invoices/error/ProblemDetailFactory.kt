package com.example.invoices.error

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.stereotype.Component
import java.net.URI
import java.time.Instant

/**
 * Builds the one response shape this service produces for errors: RFC 9457 problem+json.
 *
 * Fields are copied in explicitly. Nothing reflects over the exception, so a message, a SQL
 * fragment or a class name cannot reach the client by accident.
 */
@Component
class ProblemDetailFactory {
    fun create(
        errorCode: ErrorCode,
        detail: String,
        request: HttpServletRequest,
        violations: List<FieldViolation> = emptyList(),
        /**
         * Defaults to the code's own status. [ProblemErrorController] overrides it, because when
         * the container has already chosen a status that decision must be reported, not replaced.
         */
        status: HttpStatusCode = errorCode.status,
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail).apply {
            type = errorCode.type
            title = errorCode.title
            instance = URI.create(request.requestURI)
            setProperty("timestamp", Instant.now())
            setProperty("traceId", RequestId.of(request))
            if (violations.isNotEmpty()) {
                setProperty("errors", violations)
            }
        }

    /**
     * 5xx responses never describe what actually broke: the cause is logged against the traceId
     * instead, which is the only thing a caller needs in order to report it.
     */
    fun createServerError(
        errorCode: ErrorCode,
        request: HttpServletRequest,
        status: HttpStatusCode = errorCode.status,
    ): ProblemDetail = create(errorCode, SAFE_SERVER_DETAIL, request, status = status)

    companion object {
        const val SAFE_SERVER_DETAIL: String =
            "The request could not be completed. Quote the traceId when reporting this."
    }
}
