package com.example.invoices.repository

import com.example.invoices.model.InvoiceStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

/**
 * The persistence shape of an invoice. It lives here, next to the repository, because this package
 * is the only one allowed to know Hibernate exists - nothing outside it may accept or return an
 * entity.
 *
 * Three things are deliberate and easy to get wrong in Kotlin:
 *  - **a class, not a data class.** A generated equals/hashCode over a mutable id breaks the moment
 *    an entity is put in a set before being flushed, and it changes identity after the insert.
 *  - **`var`, not `val`.** Hibernate writes fields on load; the kotlin-jpa plugin supplies the
 *    no-arg constructor, and allOpen (see build.gradle.kts) makes the class non-final for proxies.
 *  - **every column named explicitly.** The implicit naming strategy is a silent coupling between
 *    a Kotlin rename and a production schema.
 */
@Entity
@Table(name = "invoices")
class InvoiceEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,
    @Column(name = "customer_id", nullable = false, updatable = false)
    var customerId: UUID,
    @Column(name = "amount_cents", nullable = false)
    var amountCents: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = STATUS_LENGTH)
    var status: InvoiceStatus,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0,
) {
    companion object {
        const val STATUS_LENGTH: Int = 16
    }
}
