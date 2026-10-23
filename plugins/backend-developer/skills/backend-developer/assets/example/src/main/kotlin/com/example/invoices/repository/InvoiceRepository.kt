package com.example.invoices.repository

import com.example.invoices.model.InvoiceStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Spring Data only. No @Transactional here (the service owns transaction boundaries) and no
 * business rules - a repository answers questions about rows.
 */
interface InvoiceRepository : JpaRepository<InvoiceEntity, UUID> {
    fun findAllByStatus(
        status: InvoiceStatus,
        pageable: Pageable,
    ): Page<InvoiceEntity>

    fun existsByCustomerIdAndAmountCentsAndStatus(
        customerId: UUID,
        amountCents: Long,
        status: InvoiceStatus,
    ): Boolean
}
