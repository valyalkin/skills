package com.example.invoices.service

import com.example.invoices.error.DuplicateException
import com.example.invoices.error.ErrorCode
import com.example.invoices.error.NotFoundException
import com.example.invoices.error.UnprocessableEntityException
import com.example.invoices.model.Invoice
import com.example.invoices.model.InvoiceStatus
import com.example.invoices.repository.InvoiceRepository
import com.example.invoices.repository.toEntity
import com.example.invoices.repository.toModel
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/**
 * All the business logic, and the only layer that decides an outcome is a client error.
 *
 * It takes and returns `model` types, so nothing here depends on HTTP or on JPA. The repository is
 * an interface and the clock is injected, which is the whole reason the unit tests need no Spring
 * context and no database.
 */
@Service
@Transactional(readOnly = true)
class InvoiceService(
    private val invoices: InvoiceRepository,
    private val paymentGateway: PaymentGatewayClient,
    private val clock: Clock,
) {
    @PreAuthorize("hasAuthority('SCOPE_invoices:read')")
    fun get(id: UUID): Invoice =
        invoices.findByIdOrNull(id)?.toModel()
            ?: throw NotFoundException(ErrorCode.INVOICE_NOT_FOUND, "Invoice $id was not found")

    @PreAuthorize("hasAuthority('SCOPE_invoices:read')")
    fun list(
        status: InvoiceStatus?,
        pageable: Pageable,
    ): Page<Invoice> {
        val page =
            if (status == null) {
                invoices.findAll(pageable)
            } else {
                invoices.findAllByStatus(status, pageable)
            }
        return page.map { it.toModel() }
    }

    @PreAuthorize("hasAuthority('SCOPE_invoices:write')")
    @Transactional
    fun create(command: CreateInvoice): Invoice {
        if (invoices.existsByCustomerIdAndAmountCentsAndStatus(
                command.customerId,
                command.amountCents,
                InvoiceStatus.PENDING,
            )
        ) {
            throw DuplicateException(
                ErrorCode.DUPLICATE_INVOICE,
                "Customer ${command.customerId} already has a pending invoice for ${command.amountCents}",
            )
        }

        val entity =
            Invoice(
                id = UUID.randomUUID(),
                customerId = command.customerId,
                amountCents = command.amountCents,
                status = InvoiceStatus.PENDING,
                createdAt = clock.instant(),
            ).toEntity()

        return try {
            invoices.save(entity).toModel()
        } catch (exception: DataIntegrityViolationException) {
            // The check above races: two concurrent requests both pass it and the partial unique
            // index decides. The loser gets the same 409 as if it had lost the check.
            throw DuplicateException(
                ErrorCode.DUPLICATE_INVOICE,
                "Customer ${command.customerId} already has a pending invoice for ${command.amountCents}",
                exception,
            )
        }
    }

    @PreAuthorize("hasAuthority('SCOPE_invoices:write')")
    @Transactional
    fun pay(id: UUID): Invoice {
        val entity =
            invoices.findByIdOrNull(id)
                ?: throw NotFoundException(ErrorCode.INVOICE_NOT_FOUND, "Invoice $id was not found")

        if (!entity.toModel().payable) {
            // Understood, well formed, and refused: 422, not 400.
            throw UnprocessableEntityException(
                ErrorCode.INVOICE_NOT_PAYABLE,
                "Invoice $id is ${entity.status} and cannot be paid again",
            )
        }

        paymentGateway.charge(invoiceId = entity.id, amountCents = entity.amountCents)
        entity.status = InvoiceStatus.PAID
        return invoices.save(entity).toModel()
    }
}

/** What the service needs in order to create an invoice - a model-level input, never a DTO. */
data class CreateInvoice(
    val customerId: UUID,
    val amountCents: Long,
)
