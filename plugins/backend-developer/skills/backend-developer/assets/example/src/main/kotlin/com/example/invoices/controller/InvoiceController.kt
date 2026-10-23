package com.example.invoices.controller

import com.example.invoices.dto.CreateInvoiceRequest
import com.example.invoices.dto.InvoiceResponse
import com.example.invoices.dto.PageResponse
import com.example.invoices.dto.toCommand
import com.example.invoices.dto.toResponse
import com.example.invoices.model.InvoiceStatus
import com.example.invoices.service.InvoiceService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * HTTP in, HTTP out. No business rules, no transactions, no entities.
 *
 * Every method returns the response object itself - never ResponseEntity. The status comes from
 * @ResponseStatus, which keeps it visible in the signature and in the generated OpenAPI document
 * instead of buried in a builder call. Errors are not this class's concern at all; they are thrown
 * by the service and rendered by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/v1/invoices", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "Invoices", description = "Raise and settle customer invoices")
class InvoiceController(
    private val invoices: InvoiceService,
) {
    @GetMapping("/{id}")
    @Operation(summary = "Fetch one invoice")
    @ApiResponse(responseCode = "200", description = "The invoice")
    @ApiResponse(
        responseCode = "404",
        description = "No invoice with that id (type: urn:problem:invoices:invoice-not-found)",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(implementation = ProblemDetail::class))],
    )
    fun get(
        @PathVariable id: UUID,
    ): InvoiceResponse = invoices.get(id).toResponse()

    @GetMapping
    @Operation(summary = "List invoices, newest first")
    @ApiResponse(responseCode = "200", description = "A page of invoices")
    fun list(
        @RequestParam(required = false)
        @Parameter(description = "Only invoices in this status")
        status: InvoiceStatus?,
        @PageableDefault(size = DEFAULT_PAGE_SIZE, sort = ["createdAt"])
        pageable: Pageable,
    ): PageResponse<InvoiceResponse> = invoices.list(status, pageable).toResponse { it.toResponse() }

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Raise an invoice")
    @ApiResponse(responseCode = "201", description = "The invoice was created")
    @ApiResponse(
        responseCode = "400",
        description = "Validation failed (type: urn:problem:invoices:validation-failed)",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(implementation = ProblemDetail::class))],
    )
    @ApiResponse(
        responseCode = "409",
        description = "A pending invoice already exists (type: urn:problem:invoices:duplicate-invoice)",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(implementation = ProblemDetail::class))],
    )
    fun create(
        @Valid @RequestBody request: CreateInvoiceRequest,
    ): InvoiceResponse = invoices.create(request.toCommand()).toResponse()

    @PostMapping("/{id}/payment")
    @Operation(summary = "Pay an invoice")
    @ApiResponse(responseCode = "200", description = "The settled invoice")
    @ApiResponse(
        responseCode = "422",
        description = "The invoice is already paid (type: urn:problem:invoices:invoice-not-payable)",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(implementation = ProblemDetail::class))],
    )
    @ApiResponse(
        responseCode = "503",
        description = "The payment gateway is unavailable (type: urn:problem:invoices:upstream-unavailable)",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(implementation = ProblemDetail::class))],
    )
    fun pay(
        @PathVariable id: UUID,
    ): InvoiceResponse = invoices.pay(id).toResponse()

    companion object {
        const val PROBLEM_JSON: String = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        const val DEFAULT_PAGE_SIZE: Int = 20
    }
}
