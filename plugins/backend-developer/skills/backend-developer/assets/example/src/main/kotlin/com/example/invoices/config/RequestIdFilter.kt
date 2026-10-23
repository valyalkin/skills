package com.example.invoices.config

import com.example.invoices.error.RequestId
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Gives every request an id, and makes it the same value in three places: the log lines for the
 * request (via MDC), the `X-Request-Id` response header, and the `traceId` on any problem
 * response. That is what makes a 500 reportable - the caller quotes the traceId and it finds the
 * stack trace.
 *
 * An inbound `X-Request-Id` is honoured so a trace survives across services.
 *
 * The order matters more than it looks. Spring Security registers its filter chain at
 * -100 (`spring.security.filter.order`), and it rejects an unauthenticated request
 * inside that chain. A filter at the same order, or later, never runs for those requests - so
 * every 401 and 403 comes back with `traceId: "unknown"` and no `X-Request-Id` header, which is
 * precisely the case somebody will be trying to debug.
 */
@Component
@Order(FILTER_ORDER)
class RequestIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestId = request.getHeader(RequestId.HEADER)?.takeIf { it.isNotBlank() } ?: newId()
        request.setAttribute(RequestId.ATTRIBUTE, requestId)
        response.setHeader(RequestId.HEADER, requestId)
        MDC.put(RequestId.MDC_KEY, requestId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            // A pooled thread must not carry the id into the next request.
            MDC.remove(RequestId.MDC_KEY)
        }
    }

    private fun newId(): String =
        UUID
            .randomUUID()
            .toString()
            .replace("-", "")
            .take(ID_LENGTH)
}

private const val ID_LENGTH = 16

/**
 * Ahead of Spring Security's chain, so authentication failures are traceable too.
 *
 * The chain is registered at -100 (configurable via `spring.security.filter.order`). Boot 4 no
 * longer exposes that default as a constant, so it is written out here rather than guessed at.
 */
internal const val FILTER_ORDER = -110
