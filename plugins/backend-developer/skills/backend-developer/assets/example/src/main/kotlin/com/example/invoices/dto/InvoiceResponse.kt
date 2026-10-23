package com.example.invoices.dto

import com.example.invoices.model.InvoiceStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

@Schema(description = "An invoice")
data class InvoiceResponse(
    val id: UUID,
    val customerId: UUID,
    @field:Schema(description = "Amount in minor units (cents)", example = "2500")
    val amountCents: Long,
    val status: InvoiceStatus,
    @field:Schema(description = "RFC 3339 instant, always UTC", example = "2026-10-23T09:15:00Z")
    val createdAt: Instant,
)
