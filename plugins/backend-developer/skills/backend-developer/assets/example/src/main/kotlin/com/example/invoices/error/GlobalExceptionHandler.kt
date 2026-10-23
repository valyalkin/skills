package com.example.invoices.error

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * The one place an exception becomes a response.
 *
 * Every handler returns a plain [ProblemDetail]; Spring applies its `status` to the response, so
 * there is no ResponseEntity here either - the same rule the controllers follow.
 *
 * Two things this class is responsible for beyond mapping:
 *  - **nothing escapes.** Spring's own exceptions are mapped explicitly rather than left to the
 *    framework's defaults, so no endpoint can answer with a shape other than problem+json.
 *    Failures raised before the dispatcher (a filter, say) are caught by [ProblemErrorController]
 *    and the security entry points instead - those cannot reach an @ExceptionHandler.
 *  - **nothing leaks.** 4xx detail is written here, from field names only. 5xx detail is a fixed
 *    string, with the real cause logged against the same traceId the client receives.
 */
@RestControllerAdvice
class GlobalExceptionHandler(
    private val problems: ProblemDetailFactory,
    private val violations: ViolationExtractor,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // ---------------------------------------------------------------- the service's own failures

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(
        exception: NotFoundException,
        request: HttpServletRequest,
    ): ProblemDetail = clientError(exception, request)

    @ExceptionHandler(DuplicateException::class)
    fun handleDuplicate(
        exception: DuplicateException,
        request: HttpServletRequest,
    ): ProblemDetail = clientError(exception, request)

    @ExceptionHandler(UnprocessableEntityException::class)
    fun handleUnprocessable(
        exception: UnprocessableEntityException,
        request: HttpServletRequest,
    ): ProblemDetail = clientError(exception, request)

    /** The 400 base case, and the superclass handler for any subclass added later. */
    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(
        exception: BusinessException,
        request: HttpServletRequest,
    ): ProblemDetail = clientError(exception, request)

    @ExceptionHandler(SystemException::class)
    fun handleSystem(
        exception: SystemException,
        request: HttpServletRequest,
    ): ProblemDetail {
        logServerFailure(exception.errorCode, exception, request)
        return problems.createServerError(exception.errorCode, request)
    }

    // ------------------------------------------------------------------------ request validation

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleBodyValidation(
        exception: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ProblemDetail {
        val found =
            violations.fromFieldErrors(
                exception.bindingResult.fieldErrors,
                exception.bindingResult.globalErrors,
            )
        return validationProblem(found, request)
    }

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleParameterValidation(
        exception: HandlerMethodValidationException,
        request: HttpServletRequest,
    ): ProblemDetail = validationProblem(violations.fromHandlerMethodValidation(exception), request)

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(
        exception: ConstraintViolationException,
        request: HttpServletRequest,
    ): ProblemDetail = validationProblem(violations.fromConstraintViolations(exception), request)

    /**
     * A body Jackson could not bind. The cause decides whether this is a missing field, an unknown
     * field, a wrong type or genuinely unparseable input; the parser's own message is never
     * returned, because it carries class names and internal paths.
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(
        exception: HttpMessageNotReadableException,
        request: HttpServletRequest,
    ): ProblemDetail {
        val found = violations.fromJacksonFailure(exception.cause)
        log.warn(
            "400 malformed body on {} {} (traceId={})",
            request.method,
            request.requestURI,
            RequestId.of(request),
        )
        return if (found.isEmpty()) {
            problems.create(
                ErrorCode.MALFORMED_REQUEST,
                "The request body is not valid JSON.",
                request,
            )
        } else {
            problems.create(ErrorCode.VALIDATION_FAILED, detailFor(found), request, found)
        }
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(
        exception: MethodArgumentTypeMismatchException,
        request: HttpServletRequest,
    ): ProblemDetail {
        val name = exception.name
        val expected = exception.requiredType?.simpleName ?: "the expected type"
        val found =
            listOf(
                FieldViolation(
                    pointer = "#/$name",
                    field = name,
                    code = "TypeMismatch",
                    message = "must be a valid $expected",
                ),
            )
        return validationProblem(found, request)
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParameter(
        exception: MissingServletRequestParameterException,
        request: HttpServletRequest,
    ): ProblemDetail =
        validationProblem(
            listOf(
                FieldViolation(
                    pointer = "#/${exception.parameterName}",
                    field = exception.parameterName,
                    code = "Required",
                    message = "is a required ${exception.parameterType} query parameter",
                ),
            ),
            request,
        )

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(
        exception: MissingRequestHeaderException,
        request: HttpServletRequest,
    ): ProblemDetail =
        validationProblem(
            listOf(
                FieldViolation(
                    pointer = "#/${exception.headerName}",
                    field = exception.headerName,
                    code = "Required",
                    message = "is a required header",
                ),
            ),
            request,
        )

    // ------------------------------------------------------------------------- protocol failures

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResource(
        exception: NoResourceFoundException,
        request: HttpServletRequest,
    ): ProblemDetail =
        problems.create(
            ErrorCode.ENDPOINT_NOT_FOUND,
            "No endpoint ${request.method} ${request.requestURI}.",
            request,
        )

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(
        exception: HttpRequestMethodNotSupportedException,
        request: HttpServletRequest,
    ): ProblemDetail {
        val supported = exception.supportedMethods?.joinToString(", ").orEmpty()
        return problems.create(
            ErrorCode.METHOD_NOT_ALLOWED,
            "${exception.method} is not supported here. Supported: $supported.",
            request,
        )
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleMediaTypeNotSupported(
        exception: HttpMediaTypeNotSupportedException,
        request: HttpServletRequest,
    ): ProblemDetail =
        problems.create(
            ErrorCode.UNSUPPORTED_MEDIA_TYPE,
            "Content-Type ${exception.contentType} is not supported. Use application/json.",
            request,
        )

    @ExceptionHandler(HttpMediaTypeNotAcceptableException::class)
    fun handleNotAcceptable(
        exception: HttpMediaTypeNotAcceptableException,
        request: HttpServletRequest,
    ): ProblemDetail =
        problems.create(
            ErrorCode.NOT_ACCEPTABLE,
            "No acceptable representation; this endpoint produces application/json.",
            request,
        )

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleUploadTooLarge(
        exception: MaxUploadSizeExceededException,
        request: HttpServletRequest,
    ): ProblemDetail =
        problems.create(
            ErrorCode.PAYLOAD_TOO_LARGE,
            "The request body exceeds the maximum accepted size.",
            request,
        )

    // ---------------------------------------------------------------------------------- security

    /**
     * Reached when method security rejects a call inside the dispatcher. A missing or invalid token
     * never gets this far - it is rejected in the filter chain, which is why SecurityConfig wires
     * its own entry point and denied handler to the same [ProblemDetailFactory].
     */
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(
        exception: AccessDeniedException,
        request: HttpServletRequest,
    ): ProblemDetail {
        val authentication = SecurityContextHolder.getContext().authentication
        val anonymous = authentication == null || authentication is AnonymousAuthenticationToken
        val code = if (anonymous) ErrorCode.UNAUTHENTICATED else ErrorCode.ACCESS_DENIED
        log.warn(
            "{} on {} {} (traceId={})",
            code.status.value(),
            request.method,
            request.requestURI,
            RequestId.of(request),
        )
        return problems.create(code, code.title, request)
    }

    // -------------------------------------------------------------------------------- the rest

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity(
        exception: DataIntegrityViolationException,
        request: HttpServletRequest,
    ): ProblemDetail {
        // The exception text contains the constraint name and often the offending values, so it is
        // logged and never returned.
        log.warn(
            "409 data integrity violation on {} {} (traceId={})",
            request.method,
            request.requestURI,
            RequestId.of(request),
            exception,
        )
        return problems.create(
            ErrorCode.DUPLICATE_INVOICE,
            "The request conflicts with the current state of the resource.",
            request,
        )
    }

    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleOptimisticLocking(
        exception: OptimisticLockingFailureException,
        request: HttpServletRequest,
    ): ProblemDetail =
        problems.create(
            ErrorCode.CONCURRENT_MODIFICATION,
            "The resource was modified concurrently. Re-read it and retry.",
            request,
        )

    /** The backstop. Anything unmapped is a bug in this service, so it is a 500. */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(
        exception: Exception,
        request: HttpServletRequest,
    ): ProblemDetail {
        logServerFailure(ErrorCode.INTERNAL_ERROR, exception, request)
        return problems.createServerError(ErrorCode.INTERNAL_ERROR, request)
    }

    // ---------------------------------------------------------------------------------- helpers

    private fun clientError(
        exception: BusinessException,
        request: HttpServletRequest,
    ): ProblemDetail {
        log.warn(
            "{} {} on {} {} (traceId={}): {}",
            exception.errorCode.status.value(),
            exception.errorCode.name,
            request.method,
            request.requestURI,
            RequestId.of(request),
            exception.message,
        )
        return problems.create(
            exception.errorCode,
            exception.message,
            request,
            exception.violations,
        )
    }

    private fun validationProblem(
        found: List<FieldViolation>,
        request: HttpServletRequest,
    ): ProblemDetail {
        log.warn(
            "400 validation failed on {} {} (traceId={}): {}",
            request.method,
            request.requestURI,
            RequestId.of(request),
            found.joinToString { it.field },
        )
        return problems.create(ErrorCode.VALIDATION_FAILED, detailFor(found), request, found)
    }

    /** "2 fields are invalid: amount, customer.email" - the names, never the values. */
    private fun detailFor(found: List<FieldViolation>): String =
        when (found.size) {
            0 -> "The request is invalid."
            1 -> "1 field is invalid: ${found.first().field}"
            else -> "${found.size} fields are invalid: ${found.joinToString { it.field }}"
        }

    private fun logServerFailure(
        errorCode: ErrorCode,
        exception: Throwable,
        request: HttpServletRequest,
    ) {
        log.error(
            "{} {} on {} {} (traceId={})",
            errorCode.status.value(),
            errorCode.name,
            request.method,
            request.requestURI,
            RequestId.of(request),
            exception,
        )
    }
}
