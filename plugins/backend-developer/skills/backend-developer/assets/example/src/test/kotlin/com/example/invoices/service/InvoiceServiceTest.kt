package com.example.invoices.service

import com.example.invoices.error.DuplicateException
import com.example.invoices.error.ErrorCode
import com.example.invoices.error.NotFoundException
import com.example.invoices.error.SystemException
import com.example.invoices.error.UnprocessableEntityException
import com.example.invoices.model.Invoice
import com.example.invoices.model.InvoiceStatus
import com.example.invoices.repository.InvoiceEntity
import com.example.invoices.repository.InvoiceRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

/**
 * Unit tests: no Spring context, no database, no HTTP.
 *
 * That is possible because InvoiceService depends on an interface, a client and a Clock - which is
 * the practical payoff of keeping `model` free of JPA and letting the service own the rules.
 */
class InvoiceServiceTest {
    private val repository = mockk<InvoiceRepository>()
    private val paymentGateway = mockk<PaymentGatewayClient>()
    private val now = Instant.parse("2026-10-23T09:15:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val service = InvoiceService(repository, paymentGateway, clock)

    private val customerId = UUID.fromString("6f1c7d4e-0f4a-4f1a-9a8e-2b9d2a3c4d5e")
    private val invoiceId = UUID.fromString("11111111-2222-3333-4444-555555555555")

    private fun entity(
        status: InvoiceStatus = InvoiceStatus.PENDING,
        amountCents: Long = 2500,
    ) = InvoiceEntity(
        id = invoiceId,
        customerId = customerId,
        amountCents = amountCents,
        status = status,
        createdAt = now,
    )

    @Test
    fun `get returns the invoice as a model`() {
        every { repository.findById(invoiceId) } returns Optional.of(entity())

        val invoice = service.get(invoiceId)

        assertThat(invoice)
            .isEqualTo(
                Invoice(
                    id = invoiceId,
                    customerId = customerId,
                    amountCents = 2500,
                    status = InvoiceStatus.PENDING,
                    createdAt = now,
                ),
            )
    }

    @Test
    fun `get throws NotFoundException naming the id`() {
        every { repository.findById(invoiceId) } returns Optional.empty()

        assertThatThrownBy { service.get(invoiceId) }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessageContaining(invoiceId.toString())
            .extracting { (it as NotFoundException).errorCode }
            .isEqualTo(ErrorCode.INVOICE_NOT_FOUND)
    }

    @Test
    fun `list without a status filter pages over everything`() {
        val pageable = PageRequest.of(0, 20)
        every { repository.findAll(pageable) } returns PageImpl(listOf(entity()), pageable, 1)

        val page = service.list(status = null, pageable = pageable)

        assertThat(page.content).singleElement().extracting { it.id }.isEqualTo(invoiceId)
        verify(exactly = 0) { repository.findAllByStatus(any(), any()) }
    }

    @Test
    fun `list with a status filter delegates to the derived query`() {
        val pageable = PageRequest.of(0, 20)
        every { repository.findAllByStatus(InvoiceStatus.PAID, pageable) } returns
            PageImpl(listOf(entity(status = InvoiceStatus.PAID)), pageable, 1)

        val page = service.list(status = InvoiceStatus.PAID, pageable = pageable)

        assertThat(page.content).singleElement().extracting { it.status }.isEqualTo(InvoiceStatus.PAID)
    }

    @Test
    fun `create stamps the invoice with the injected clock`() {
        every {
            repository.existsByCustomerIdAndAmountCentsAndStatus(customerId, 2500, InvoiceStatus.PENDING)
        } returns false
        every { repository.save(any()) } answers { firstArg() }

        val invoice = service.create(CreateInvoice(customerId, 2500))

        assertThat(invoice.createdAt).isEqualTo(now)
        assertThat(invoice.status).isEqualTo(InvoiceStatus.PENDING)
        assertThat(invoice.id).isNotNull()
    }

    @Test
    fun `create rejects a second pending invoice for the same customer and amount`() {
        every {
            repository.existsByCustomerIdAndAmountCentsAndStatus(customerId, 2500, InvoiceStatus.PENDING)
        } returns true

        assertThatThrownBy { service.create(CreateInvoice(customerId, 2500)) }
            .isInstanceOf(DuplicateException::class.java)
            .extracting { (it as DuplicateException).errorCode }
            .isEqualTo(ErrorCode.DUPLICATE_INVOICE)

        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `create turns a lost race on the unique index into the same 409`() {
        every {
            repository.existsByCustomerIdAndAmountCentsAndStatus(customerId, 2500, InvoiceStatus.PENDING)
        } returns false
        every { repository.save(any()) } throws DataIntegrityViolationException("ux_invoices_customer_amount_pending")

        assertThatThrownBy { service.create(CreateInvoice(customerId, 2500)) }
            .isInstanceOf(DuplicateException::class.java)
            .hasCauseInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `pay charges the gateway and marks the invoice paid`() {
        every { repository.findById(invoiceId) } returns Optional.of(entity())
        every { paymentGateway.charge(invoiceId, 2500) } returns "ch_123"
        every { repository.save(any()) } answers { firstArg() }

        val invoice = service.pay(invoiceId)

        assertThat(invoice.status).isEqualTo(InvoiceStatus.PAID)
        verify(exactly = 1) { paymentGateway.charge(invoiceId, 2500) }
    }

    @Test
    fun `pay refuses an already paid invoice with 422, not 400`() {
        every { repository.findById(invoiceId) } returns Optional.of(entity(status = InvoiceStatus.PAID))

        assertThatThrownBy { service.pay(invoiceId) }
            .isInstanceOf(UnprocessableEntityException::class.java)
            .extracting { (it as UnprocessableEntityException).errorCode }
            .isEqualTo(ErrorCode.INVOICE_NOT_PAYABLE)

        verify(exactly = 0) { paymentGateway.charge(any(), any()) }
    }

    @Test
    fun `pay propagates a gateway failure as a SystemException and does not save`() {
        every { repository.findById(invoiceId) } returns Optional.of(entity())
        every { paymentGateway.charge(invoiceId, 2500) } throws
            SystemException("gateway down", ErrorCode.UPSTREAM_UNAVAILABLE)

        assertThatThrownBy { service.pay(invoiceId) }
            .isInstanceOf(SystemException::class.java)
            .extracting { (it as SystemException).errorCode }
            .isEqualTo(ErrorCode.UPSTREAM_UNAVAILABLE)

        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `pay on a missing invoice is a 404`() {
        every { repository.findById(invoiceId) } returns Optional.empty()

        assertThatThrownBy { service.pay(invoiceId) }.isInstanceOf(NotFoundException::class.java)
    }
}
