package com.example.invoices.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.util.UUID

/**
 * The create payload.
 *
 * The types are non-nullable, so an absent field cannot reach the service as null - Jackson fails
 * the bind and GlobalExceptionHandler reports it by name with code `Required`. @field:NotNull is
 * still declared: it documents the constraint in the OpenAPI schema and keeps the error shape the
 * same whether the field is missing or explicitly null.
 */
@Schema(description = "A new invoice for a customer")
data class CreateInvoiceRequest(
    @field:NotNull(message = "is required")
    @field:Schema(
        description = "Customer the invoice is raised against",
        example = "6f1c7d4e-0f4a-4f1a-9a8e-2b9d2a3c4d5e",
    )
    val customerId: UUID,
    @field:NotNull(message = "is required")
    @field:Positive(message = "must be greater than 0")
    @field:Max(value = MAX_AMOUNT_CENTS, message = "must not exceed 1,000,000.00")
    @field:Schema(description = "Amount in minor units (cents)", example = "2500")
    val amountCents: Long,
) {
    companion object {
        const val MAX_AMOUNT_CENTS: Long = 100_000_000
    }
}
