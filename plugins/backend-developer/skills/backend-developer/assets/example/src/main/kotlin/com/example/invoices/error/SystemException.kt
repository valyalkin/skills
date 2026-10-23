package com.example.invoices.error

/**
 * The service or something it depends on failed. Always a 5xx, and never the caller's fault.
 *
 * The [message] is for the log, not for the response: GlobalExceptionHandler replaces the detail
 * of every 5xx with a fixed, safe string and correlates the two through the traceId.
 */
class SystemException(
    override val message: String,
    val errorCode: ErrorCode = ErrorCode.INTERNAL_ERROR,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    init {
        require(errorCode.status.is5xxServerError) {
            "SystemException needs a 5xx ErrorCode, but $errorCode is ${errorCode.status}"
        }
    }
}
