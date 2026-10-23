package com.example.invoices.error

import org.springframework.http.HttpStatus
import java.net.URI

/**
 * The service's error catalogue. Every error response carries one of these as its `type`.
 *
 * The `type` URN is API surface: clients branch on it, it is documented on each endpoint's
 * @ApiResponse, and it therefore ends up in contracts/api.yaml. Renaming one is a breaking
 * change; adding one is not.
 */
enum class ErrorCode(
    val status: HttpStatus,
    val type: URI,
    val title: String,
) {
    // 400
    VALIDATION_FAILED(
        HttpStatus.BAD_REQUEST,
        URI.create("urn:problem:invoices:validation-failed"),
        "Request validation failed",
    ),
    MALFORMED_REQUEST(
        HttpStatus.BAD_REQUEST,
        URI.create("urn:problem:invoices:malformed-request"),
        "Malformed request",
    ),

    // 401 / 403
    UNAUTHENTICATED(
        HttpStatus.UNAUTHORIZED,
        URI.create("urn:problem:invoices:unauthenticated"),
        "Authentication required",
    ),
    ACCESS_DENIED(
        HttpStatus.FORBIDDEN,
        URI.create("urn:problem:invoices:access-denied"),
        "Access denied",
    ),

    // 404
    INVOICE_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        URI.create("urn:problem:invoices:invoice-not-found"),
        "Invoice not found",
    ),
    ENDPOINT_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        URI.create("urn:problem:invoices:endpoint-not-found"),
        "Endpoint not found",
    ),

    // 405 / 406 / 413 / 415
    METHOD_NOT_ALLOWED(
        HttpStatus.METHOD_NOT_ALLOWED,
        URI.create("urn:problem:invoices:method-not-allowed"),
        "Method not allowed",
    ),
    NOT_ACCEPTABLE(
        HttpStatus.NOT_ACCEPTABLE,
        URI.create("urn:problem:invoices:not-acceptable"),
        "No acceptable representation",
    ),
    PAYLOAD_TOO_LARGE(
        HttpStatus.PAYLOAD_TOO_LARGE,
        URI.create("urn:problem:invoices:payload-too-large"),
        "Payload too large",
    ),
    UNSUPPORTED_MEDIA_TYPE(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        URI.create("urn:problem:invoices:unsupported-media-type"),
        "Unsupported media type",
    ),

    // 409
    DUPLICATE_INVOICE(
        HttpStatus.CONFLICT,
        URI.create("urn:problem:invoices:duplicate-invoice"),
        "Invoice already exists",
    ),
    CONCURRENT_MODIFICATION(
        HttpStatus.CONFLICT,
        URI.create("urn:problem:invoices:concurrent-modification"),
        "Concurrent modification",
    ),

    // 422
    INVOICE_NOT_PAYABLE(
        HttpStatus.UNPROCESSABLE_ENTITY,
        URI.create("urn:problem:invoices:invoice-not-payable"),
        "Invoice is not payable",
    ),

    // 5xx
    INTERNAL_ERROR(
        HttpStatus.INTERNAL_SERVER_ERROR,
        URI.create("urn:problem:invoices:internal-error"),
        "Internal server error",
    ),
    UPSTREAM_UNAVAILABLE(
        HttpStatus.SERVICE_UNAVAILABLE,
        URI.create("urn:problem:invoices:upstream-unavailable"),
        "Upstream service unavailable",
    ),
}
