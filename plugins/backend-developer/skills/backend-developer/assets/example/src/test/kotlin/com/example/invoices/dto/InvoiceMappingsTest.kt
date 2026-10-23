package com.example.invoices.dto

import com.example.invoices.model.Invoice
import com.example.invoices.model.InvoiceStatus
import com.example.invoices.repository.toEntity
import com.example.invoices.repository.toModel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.Instant
import java.util.UUID

class InvoiceMappingsTest {
    private val invoice =
        Invoice(
            id = UUID.fromString("11111111-2222-3333-4444-555555555555"),
            customerId = UUID.fromString("6f1c7d4e-0f4a-4f1a-9a8e-2b9d2a3c4d5e"),
            amountCents = 2500,
            status = InvoiceStatus.PENDING,
            createdAt = Instant.parse("2026-10-23T09:15:00Z"),
        )

    @Test
    fun `model maps to response field for field`() {
        val response = invoice.toResponse()

        assertThat(response.id).isEqualTo(invoice.id)
        assertThat(response.customerId).isEqualTo(invoice.customerId)
        assertThat(response.amountCents).isEqualTo(invoice.amountCents)
        assertThat(response.status).isEqualTo(invoice.status)
        assertThat(response.createdAt).isEqualTo(invoice.createdAt)
    }

    @Test
    fun `entity round-trips through the model unchanged`() {
        assertThat(invoice.toEntity().toModel()).isEqualTo(invoice)
    }

    @Test
    fun `request maps to the service command`() {
        val request = CreateInvoiceRequest(customerId = invoice.customerId, amountCents = 2500)

        val command = request.toCommand()

        assertThat(command.customerId).isEqualTo(invoice.customerId)
        assertThat(command.amountCents).isEqualTo(2500)
    }

    @Test
    fun `a pending invoice is payable and a paid one is not`() {
        assertThat(invoice.payable).isTrue()
        assertThat(invoice.copy(status = InvoiceStatus.PAID).payable).isFalse()
    }

    @Test
    fun `page maps into the service's own envelope`() {
        // The last page of 25 across pages of 20 holds 5 rows. Giving PageImpl a content list that
        // does not match the page size makes it recompute the total, which is a trap worth avoiding
        // in tests as much as in production code.
        val lastPage = List(5) { invoice }
        val page = PageImpl(lastPage, PageRequest.of(1, 20), 25)

        val response = page.toResponse { it.toResponse() }

        assertThat(response.content).hasSize(5).allSatisfy({ assertThat(it.id).isEqualTo(invoice.id) })
        assertThat(response.page).isEqualTo(1)
        assertThat(response.size).isEqualTo(20)
        assertThat(response.totalElements).isEqualTo(25)
        assertThat(response.totalPages).isEqualTo(2)
    }
}
