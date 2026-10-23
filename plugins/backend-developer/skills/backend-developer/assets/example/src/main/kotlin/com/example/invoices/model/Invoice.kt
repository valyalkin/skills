package com.example.invoices.model

import java.time.Instant
import java.util.UUID

/**
 * The domain type. Immutable, `val` only, and free of annotations from JPA, Jackson and Swagger.
 *
 * Nothing on this class knows how an invoice is stored or how it is serialised, which is what lets
 * InvoiceService be unit-tested without Hibernate and lets the JSON contract change without
 * touching business logic.
 */
data class Invoice(
    val id: UUID,
    val customerId: UUID,
    val amountCents: Long,
    val status: InvoiceStatus,
    val createdAt: Instant,
) {
    val payable: Boolean get() = status == InvoiceStatus.PENDING
}
