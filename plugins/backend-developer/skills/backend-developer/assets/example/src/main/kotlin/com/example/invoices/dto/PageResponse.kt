package com.example.invoices.dto

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.domain.Page

/**
 * The service's own page envelope.
 *
 * Spring Data's `Page` is deliberately not returned directly: its JSON is an implementation detail
 * of the framework, it is unstable across versions, and springdoc documents it as a sprawling
 * schema. This is five fields the client can rely on.
 */
@Schema(description = "A page of results")
data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

fun <S : Any, T : Any> Page<S>.toResponse(map: (S) -> T): PageResponse<T> =
    PageResponse(
        content = content.map(map),
        page = number,
        size = size,
        totalElements = totalElements,
        totalPages = totalPages,
    )
