package com.example.invoices.error

import jakarta.servlet.http.HttpServletRequest

/**
 * Where the traceId on a problem response comes from.
 *
 * [RequestIdFilter] puts the id on the request as an attribute, so the value in the response body
 * is the same one in the log line for that request. If the filter did not run - a problem produced
 * by the container's own error dispatch, say - fall back to the inbound header.
 */
object RequestId {
    const val ATTRIBUTE: String = "com.example.invoices.requestId"
    const val HEADER: String = "X-Request-Id"
    const val MDC_KEY: String = "requestId"

    fun of(request: HttpServletRequest): String =
        request.getAttribute(ATTRIBUTE) as? String
            ?: request.getHeader(HEADER)
            ?: "unknown"
}
