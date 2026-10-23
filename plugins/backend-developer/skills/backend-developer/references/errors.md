# Errors

One rule: **every** error response is RFC 9457 `application/problem+json`, carries a stable `type`
URN and a `traceId`, and names what was wrong. Working code: `../assets/example/src/main/kotlin/com/example/invoices/error/`.

## The exception tree

```kotlin
open class BusinessException(          // 400, the caller can fix it
    val errorCode: ErrorCode,
    override val message: String,
    val violations: List<FieldViolation> = emptyList(),
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    init { require(errorCode.status.is4xxClientError) { "..." } }
}

class NotFoundException(...)             : BusinessException  // init: require status == 404
class DuplicateException(...)            : BusinessException  // init: require status == 409
class UnprocessableEntityException(...)  : BusinessException  // init: require status == 422
class SystemException(...)               : RuntimeException   // init: require status is5xx
```

The `init` guards are the point: a subclass constructed with an `ErrorCode` whose status does not
match fails immediately, at the throw site, instead of returning a plausible-looking wrong status.

`ErrorCode` is the catalogue — status, `type` URN, title:

```kotlin
enum class ErrorCode(val status: HttpStatus, val type: URI, val title: String) {
    INVOICE_NOT_FOUND(HttpStatus.NOT_FOUND, URI.create("urn:problem:invoices:invoice-not-found"), "Invoice not found"),
    ...
}
```

The URN is API surface: clients branch on it, it appears in each `@ApiResponse`, and it lands in
`contracts/api.yaml`. Renaming one is a breaking change. A test asserts every code is namespaced
and that no two share a URN.

**Only `service/` throws these.** Controllers never catch. Repositories know nothing about HTTP.

## The handler

`@RestControllerAdvice`, and every method returns a plain `ProblemDetail`:

```kotlin
@ExceptionHandler(NotFoundException::class)
fun handleNotFound(exception: NotFoundException, request: HttpServletRequest): ProblemDetail =
    clientError(exception, request)
```

Spring applies the `ProblemDetail`'s own `status` to the response, so no `ResponseEntity` is
needed — the advice follows the same rule as the controllers. There is one handler per custom
type (`NotFoundException`, `DuplicateException`, `UnprocessableEntityException`,
`BusinessException`, `SystemException`); Spring picks the most specific, and the `BusinessException`
handler automatically covers any subclass added later.

Logging: 4xx at WARN with no stack trace, 5xx at ERROR with the cause, both quoting the traceId
the client received.

**Do not extend `ResponseEntityExceptionHandler`.** Its handlers return `ResponseEntity<Object>`
and reintroduce the type the project has banned, and its default bodies do not carry `traceId` or
`errors`. Map the framework's exceptions explicitly instead — the full list is below.

## Framework exceptions that must be mapped

Each of these otherwise answers in Spring's own shape, which is *not* this service's contract:

| Exception | Status | Must report |
| --- | --- | --- |
| `MethodArgumentNotValidException` | 400 | Every field error, with pointer + constraint name |
| `HandlerMethodValidationException` | 400 | The offending parameter |
| `ConstraintViolationException` | 400 | The property path |
| `HttpMessageNotReadableException` | 400 | Missing/unknown/wrong-typed field by name — never the parser's message |
| `MethodArgumentTypeMismatchException` | 400 | Parameter name + expected type |
| `MissingServletRequestParameterException` | 400 | Parameter name |
| `MissingRequestHeaderException` | 400 | Header name |
| `NoResourceFoundException` | 404 | Method + path |
| `HttpRequestMethodNotSupportedException` | 405 | What is supported |
| `HttpMediaTypeNotSupportedException` | 415 | What to send instead |
| `HttpMediaTypeNotAcceptableException` | 406 | — |
| `MaxUploadSizeExceededException` | 413 | — |
| `DataIntegrityViolationException` | 409 | Nothing: the message contains the constraint name and values, so it is logged only |
| `OptimisticLockingFailureException` | 409 | Re-read and retry |
| `AccessDeniedException` | 401/403 | 401 if anonymous, 403 if authenticated |
| `Exception` | 500 | Nothing but the traceId |

## Naming the field: the part that gets skipped

`ViolationExtractor` turns each framework failure into `FieldViolation(pointer, field, code, message)`:

- **pointer** is an RFC 6901 JSON Pointer (`#/lines/2/quantity`), built from `FieldError.field`,
  `ConstraintViolation.propertyPath`, or Jackson's `path`. A dotted name alone is ambiguous for
  array elements; the pointer is not.
- **code** is stable and machine-readable — the constraint annotation name (`NotBlank`, `Positive`),
  or `Required`, `TypeMismatch`, `Unknown`. Clients branch on it. **message** is for people and may
  be reworded.
- **no `rejectedValue`.** Request bodies carry personal data and problem responses get logged
  downstream. The *expected type* goes in the message instead.

Specific cases, all asserted in `GlobalExceptionHandlerIT`:

| Request | Answer |
| --- | --- |
| `{"amountCents":-1}` | `pointer: #/amountCents, code: Positive` |
| `{}` (required field absent) | `code: Required`, named — not "malformed JSON" |
| `{"amountCents":"abc"}` | `code: TypeMismatch, message: must be a valid Long`; `abc` never echoed |
| `{"discountPct":10}` (unknown) | `code: Unknown`, named |
| `{not json` | 400 `malformed-request`, parser message withheld |
| `/invoices/not-a-uuid` | `field: id, code: TypeMismatch` |

Jackson's defaults fight two of these, so `JacksonConfig` sets them explicitly:
`FAIL_ON_UNKNOWN_PROPERTIES` is **off by default in Jackson 3** — without enabling it a misspelled
field is silently dropped, and the client ships a bug that looks like a server bug.

## Nothing escapes: the two paths the advice cannot see

An `@ExceptionHandler` only sees failures raised inside the `DispatcherServlet`.

1. **Security.** A missing or invalid token is rejected in the filter chain. Both the resource
   server's entry point *and* `exceptionHandling`'s must be wired — see `auth.md`.
2. **Container error dispatch.** A failure in a servlet filter, or a response the container
   produces itself, goes to `/error`, where Boot's `BasicErrorController` would answer in a
   different JSON shape. Declaring an `ErrorController` bean replaces it:

```kotlin
@RestController
class ProblemErrorController(...) : ErrorController {
    @RequestMapping("\${server.error.path:\${error.path:/error}}")
    fun handleError(request: HttpServletRequest): ProblemDetail { ... }
}
```

It reports **the status the container chose** and only decides which `type` describes it. Two
details that bite:

- Match on the numeric status code, not on an `HttpStatus` constant. Several codes have more than
  one constant — 413 is both `PAYLOAD_TOO_LARGE` and, since RFC 9110, `CONTENT_TOO_LARGE` — and
  `HttpStatus.resolve` does not promise which it returns. An identity comparison against the wrong
  alias silently falls through and rewrites the status.
- Set `server.error.whitelabel.enabled: false`, so a gap is a visible failure rather than an HTML page.

## Keeping the guarantee

`GlobalExceptionHandlerIT` drives one real request per error shape — 404 (resource and path), 409,
422, bean validation, missing field, wrong type, unknown property, malformed JSON, bad path
variable, missing param, missing header, 405, 415, 500, 503 — and asserts for each: the status, the
`type`, a non-`unknown` `traceId`, the `X-Request-Id` header, `application/problem+json`, the exact
pointers and codes, and the **absence** of parser text, SQL, class names and rejected values.
`GlobalExceptionHandlerTest` covers the handlers that are awkward to provoke over HTTP (optimistic
lock, oversized upload, 406).

Without that pair, "every error is a ProblemDetail" is an intention. With it, it is a property of
the build.
