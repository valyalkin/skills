package com.example.invoices.dto

import com.example.invoices.model.Invoice
import com.example.invoices.service.CreateInvoice

/** Model -> response and request -> command, in the package allowed to see both sides. */
fun Invoice.toResponse(): InvoiceResponse =
    InvoiceResponse(
        id = id,
        customerId = customerId,
        amountCents = amountCents,
        status = status,
        createdAt = createdAt,
    )

fun CreateInvoiceRequest.toCommand(): CreateInvoice =
    CreateInvoice(
        customerId = customerId,
        amountCents = amountCents,
    )
