package com.example.invoices.error

import jakarta.servlet.RequestDispatcher
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.boot.web.error.ErrorAttributeOptions
import org.springframework.boot.webmvc.error.ErrorAttributes
import org.springframework.boot.webmvc.error.ErrorController
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.request.ServletWebRequest

/**
 * The last gap closed.
 *
 * An @ExceptionHandler only sees failures raised inside the DispatcherServlet. A failure in a
 * servlet filter, or a container-level error dispatch, bypasses it entirely and would otherwise be
 * answered by Boot's BasicErrorController in a different JSON shape. Declaring an [ErrorController]
 * bean replaces it, so even those responses are problem+json.
 */
@RestController
class ProblemErrorController(
    private val problems: ProblemDetailFactory,
    private val errorAttributes: ErrorAttributes,
) : ErrorController {
    private val log = LoggerFactory.getLogger(javaClass)

    @RequestMapping("\${server.error.path:\${error.path:/error}}")
    fun handleError(request: HttpServletRequest): ProblemDetail {
        val status = statusOf(request)
        val errorCode = errorCodeFor(status)
        if (status.is5xxServerError) {
            // Nothing reached a handler, so log the attributes to keep the traceId useful.
            val attributes =
                errorAttributes.getErrorAttributes(
                    ServletWebRequest(request),
                    ErrorAttributeOptions.defaults(),
                )
            log.error(
                "{} error dispatch for {} (traceId={}): {}",
                status.value(),
                attributes["path"],
                RequestId.of(request),
                attributes["error"],
            )
            return problems.createServerError(errorCode, request, status)
        }
        return problems.create(errorCode, errorCode.title, request, status = status)
    }

    /**
     * The status the container chose. It is reported as-is: this controller only decides which
     * `type` describes it, never what the status should have been.
     */
    private fun statusOf(request: HttpServletRequest): HttpStatusCode {
        val code =
            request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) as? Int
                ?: return HttpStatus.INTERNAL_SERVER_ERROR
        // valueOf rejects anything that is not a three-digit code; a container that sets something
        // odd must not turn this controller itself into the failure.
        return runCatching { HttpStatusCode.valueOf(code) }
            .getOrElse { HttpStatus.INTERNAL_SERVER_ERROR }
    }

    /**
     * Matched on the numeric code rather than on an HttpStatus constant, deliberately: several
     * codes have more than one constant (413 is both PAYLOAD_TOO_LARGE and, since RFC 9110,
     * CONTENT_TOO_LARGE), and `HttpStatus.resolve` does not promise which one it returns. An
     * identity comparison against the wrong alias silently falls through to the else branch.
     */
    private fun errorCodeFor(status: HttpStatusCode): ErrorCode =
        when (status.value()) {
            HttpStatus.NOT_FOUND.value() -> ErrorCode.ENDPOINT_NOT_FOUND
            HttpStatus.UNAUTHORIZED.value() -> ErrorCode.UNAUTHENTICATED
            HttpStatus.FORBIDDEN.value() -> ErrorCode.ACCESS_DENIED
            HttpStatus.METHOD_NOT_ALLOWED.value() -> ErrorCode.METHOD_NOT_ALLOWED
            HttpStatus.UNSUPPORTED_MEDIA_TYPE.value() -> ErrorCode.UNSUPPORTED_MEDIA_TYPE
            HttpStatus.PAYLOAD_TOO_LARGE.value() -> ErrorCode.PAYLOAD_TOO_LARGE
            else -> if (status.is4xxClientError) ErrorCode.MALFORMED_REQUEST else ErrorCode.INTERNAL_ERROR
        }
}
