package com.example.invoices.repository

import com.example.invoices.model.Invoice

/**
 * Entity <-> model mapping, as extension functions in the package that is allowed to see both.
 * No mapper class and no MapStruct: the mapping is three lines and reads better next to the type.
 */
fun InvoiceEntity.toModel(): Invoice =
    Invoice(
        id = id,
        customerId = customerId,
        amountCents = amountCents,
        status = status,
        createdAt = createdAt,
    )

fun Invoice.toEntity(): InvoiceEntity =
    InvoiceEntity(
        id = id,
        customerId = customerId,
        amountCents = amountCents,
        status = status,
        createdAt = createdAt,
    )
