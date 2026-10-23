package com.example.failures

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Endpoints that exist only to provoke failures no legitimate request to the real API can produce.
 * Registered as a @Bean by GlobalExceptionHandlerIT, never by the component scan.
 *
 * It lives in `com.example.failures`, outside the application's scan root
 * (`com.example.invoices`), for a reason worth knowing: the component scan covers the test
 * classpath too. A @RestController anywhere under the scan root would be picked up by the scan as
 * well as by the @Bean method, and every context in the suite would fail to start with an
 * ambiguous mapping. Dropping the stereotype is not an alternative - Spring Framework 7 detects a
 * handler by its @Controller annotation, so a class with only @RequestMapping is never mapped.
 */
@RestController
@RequestMapping("/api/v1/test-failures")
class FailureController {
    @GetMapping("/unexpected")
    fun unexpected(): Nothing = error("column amount_cents does not exist")

    @GetMapping("/required-param")
    fun requiredParam(
        @RequestParam reference: String,
    ): Map<String, String> = mapOf("reference" to reference)

    @GetMapping("/required-header")
    fun requiredHeader(
        @RequestHeader("X-Tenant-Id") tenantId: String,
    ): Map<String, String> = mapOf("tenantId" to tenantId)
}
