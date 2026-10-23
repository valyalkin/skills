# Controllers, DTOs and the published contract

## Controllers return objects

```kotlin
@RestController
@RequestMapping("/api/v1/invoices", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "Invoices", description = "Raise and settle customer invoices")
class InvoiceController(private val invoices: InvoiceService) {

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Raise an invoice")
    @ApiResponse(responseCode = "201", description = "The invoice was created")
    @ApiResponse(
        responseCode = "409",
        description = "A pending invoice already exists (type: urn:problem:invoices:duplicate-invoice)",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(implementation = ProblemDetail::class))],
    )
    fun create(@Valid @RequestBody request: CreateInvoiceRequest): InvoiceResponse =
        invoices.create(request.toCommand()).toResponse()
}
```

No `ResponseEntity`. The status lives in `@ResponseStatus`, where it is visible in the signature
and in the generated document instead of buried in a builder call. For the rare dynamic case
(`200` versus `304`), write two endpoints or set the status on the injected `HttpServletResponse` —
not a `ResponseEntity`.

| Outcome | How |
| --- | --- |
| 200 with a body | Return the dto |
| 201 | `@ResponseStatus(CREATED)`, return the dto |
| 204 | `@ResponseStatus(NO_CONTENT)`, return `Unit` |
| 4xx/5xx | **Throw.** The advice renders it |

A controller contains no rules, no transactions, no entity and no `try`/`catch`. Three lines is a
healthy length: map the request, call the service, map the result.

Version in the path (`/api/v1`). It is the cheapest form, it is visible in logs, and a second
version is a second `@RequestMapping`, not a content-negotiation scheme.

## DTOs

```kotlin
data class CreateInvoiceRequest(
    @field:NotNull(message = "is required")
    val customerId: UUID,
    @field:NotNull(message = "is required")
    @field:Positive(message = "must be greater than 0")
    @field:Max(value = MAX_AMOUNT_CENTS, message = "must not exceed 1,000,000.00")
    @field:Schema(description = "Amount in minor units (cents)", example = "2500")
    val amountCents: Long,
)
```

- **Immutable `data class`, non-nullable types.** A missing field then cannot arrive as `null`:
  Jackson fails the bind and the handler reports it by name with `code: Required`.
- **`@field:` is required**, not optional styling. Without the use-site target the annotation lands
  on the constructor parameter and neither Bean Validation nor the schema generator sees it.
- Declare `@field:NotNull` anyway, alongside the non-nullable type: it documents the constraint in
  the OpenAPI schema and keeps the error identical whether the field is absent or explicitly null.
- Validation messages are fragments that read after the field name — "must be greater than 0", not
  "amountCents must be greater than 0". The handler supplies the name.
- No entity and no `model` type ever appears in a controller signature. Mapping is
  `toResponse()`/`toCommand()` in `dto/`.
- Return the service's own `PageResponse`, never Spring Data's `Page`: its JSON is unstable across
  versions and springdoc documents it as a sprawling schema.

`@Valid` on `@RequestBody` is what triggers validation. Omit it and every constraint above is
decoration.

## Swagger / OpenAPI

springdoc-openapi **3.1.x** for Boot 4 (2.x is the Boot 3 line). `/swagger-ui.html` and
`/v3/api-docs`, both public.

Every endpoint carries `@Operation` and an `@ApiResponse` per outcome **including the errors**,
each naming the `type` URN it uses. That is what makes the document a contract rather than a
listing of happy paths.

The `OpenAPI` bean declares the bearer scheme and **a single relative server**:

```kotlin
.servers(listOf(Server().url("/").description("This service")))
```

Left to itself springdoc writes the absolute URL of whichever port the application is listening
on, so the exported document differs on every run and a committed contract becomes permanent diff
noise.

## The contract is generated, not written

`OpenApiExportIT` fetches `/v3/api-docs.yaml` from the running application and writes
`contracts/api.yaml`, which is committed. The frontend generates its request/response types from
that file (see the `frontend-developer` skill, which forbids hand-written API types for the same
reason).

The same test asserts the document is complete: that each endpoint declares its error responses,
that `ProblemDetail` is in `components.schemas`, and that the bearer scheme is present. An endpoint
added without its 404 fails the build.

Two gotchas:

- Regenerate and commit `contracts/api.yaml` with any contract change. A stale file is worse than
  no file, because the frontend trusts it.
- `/v3/api-docs.yaml` is **not** matched by `/v3/api-docs/**`. Both paths need to be public, or the
  export gets a 401 and the only symptom is a failing test.
