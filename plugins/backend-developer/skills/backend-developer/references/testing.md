# Testing

Two layers, both mandatory, distinguished by filename so a reader and a CI split can tell them
apart: `*Test` (unit, no Spring context, milliseconds) and `*IT` (integration, full context,
Docker).

| Layer | Tools | Covers |
| --- | --- | --- |
| Unit | JUnit 5 + **AssertJ** + MockK | Services, mappings, the error handler, pure logic |
| Integration | `@SpringBootTest` + `RestTestClient` + Testcontainers + WireMock | Endpoints, security, schema, the published contract |

## Unit tests

No Spring context. The service takes an interface, a client and a `Clock`, so it needs none:

```kotlin
class InvoiceServiceTest {
    private val repository = mockk<InvoiceRepository>()
    private val paymentGateway = mockk<PaymentGatewayClient>()
    private val clock = Clock.fixed(Instant.parse("2026-10-23T09:15:00Z"), ZoneOffset.UTC)
    private val service = InvoiceService(repository, paymentGateway, clock)

    @Test
    fun `pay refuses an already paid invoice with 422, not 400`() {
        every { repository.findById(invoiceId) } returns Optional.of(entity(status = PAID))

        assertThatThrownBy { service.pay(invoiceId) }
            .isInstanceOf(UnprocessableEntityException::class.java)
            .extracting { (it as UnprocessableEntityException).errorCode }
            .isEqualTo(ErrorCode.INVOICE_NOT_PAYABLE)

        verify(exactly = 0) { paymentGateway.charge(any(), any()) }
    }
}
```

- **AssertJ throughout**: `assertThat`, `assertThatThrownBy(...).isInstanceOf(...)`,
  `singleElement()`, `satisfies { }`, `containsExactlyInAnyOrder`. One assertion style across the
  codebase.
- Assert the **error code**, not just the exception type — that is what the status and the client's
  `type` URN are derived from.
- `verify(exactly = 0)` is as valuable as the happy path: it proves the gateway was not charged and
  nothing was saved when a rule refused.
- Inject `Clock`; never assert "roughly now".

## Integration tests

One base class, one Postgres and one WireMock for the whole suite:

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
abstract class AbstractIntegrationTest {
    @Autowired protected lateinit var client: RestTestClient
    @Autowired protected lateinit var invoices: InvoiceRepository

    @BeforeEach
    fun resetState() {
        invoices.deleteAll()
        wireMock.resetAll()
        stubJwks()
    }

    companion object {
        @JvmStatic @ServiceConnection
        val postgres: PostgreSQLContainer = PostgreSQLContainer("postgres:18-alpine")

        @JvmStatic
        val wireMock = WireMockServer(WireMockConfiguration.options().dynamicPort().http2PlainDisabled(true))

        init { postgres.start(); wireMock.start() }

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("invoices.auth.jwk-set-uri") { wireMock.baseUrl() + JWKS_PATH }
            registry.add("invoices.payment-gateway.base-url") { wireMock.baseUrl() }
        }
    }
}
```

- `@ServiceConnection` hands the JDBC url, user and password to Boot, so **no datasource property
  is declared anywhere** for tests.
- The containers start in the companion's initialiser and are shared by every subclass: the suite
  pays for one Postgres, not one per class. That is also why the test task sets
  `maxParallelForks = 1` — parallel forks would each start their own.
- `@BeforeEach` truncates and re-stubs. Tests that depend on each other's leftovers are worse than
  no tests.
- `RestTestClient` (`@AutoConfigureRestTestClient`) is the Boot 4 client; `TestRestTemplate` is
  deprecated and now lives in a separate `spring-boot-resttestclient` artifact that
  `spring-boot-starter-test` does not pull in.

Mock at the **network** boundary, not by stubbing your own client class: WireMock means the
`RestClient`'s URL building, serialization, timeouts and error mapping are all under test too.

## What the integration tests must cover

| Test | Asserts |
| --- | --- |
| `<Thing>ControllerIT` | Happy paths, persistence, pagination, the correlation header |
| `GlobalExceptionHandlerIT` | **One request per error shape** — status, `type`, `traceId`, pointers, and that nothing leaked |
| `SecurityIT` | No/expired/wrong-audience/wrong-issuer/garbage token, insufficient scope, public paths |
| `SchemaValidationIT` | Flyway history, columns, indexes, constraints — the entity matches the migration |
| `OpenApiExportIT` | Exports `contracts/api.yaml`; fails if an endpoint lacks its error responses |

For a repository-only question, `@DataJpaTest` against the same Testcontainers Postgres — never H2
(`persistence.md`).

## Coverage

Kover, gated in `check`: **90% line, 80% branch**. The example sits at ~97% / ~82%.

The exclusion list is short and stated honestly:

```kotlin
classes(
    "com.example.invoices.ApplicationKt",
    "com.example.invoices.Application",
    "com.example.invoices.config.*",      // wiring, exercised by every context that boots
    "com.example.invoices.dto.*Request",  // data holders
    "com.example.invoices.dto.*Response",
    "com.example.invoices.repository.*Entity",
)
```

Kotlin generates `copy`, `equals`, `hashCode` and `componentN` on every data class. Counting those
synthetic members pads the numerator with code nobody wrote and nobody can meaningfully test, which
is how a 90% gate gets gamed into meaninglessness. Excluding them makes the number mean "the logic
is covered". Everything with a branch in it — services, the handler, the extractor, the error
controller — stays in.

Chase the gate by writing tests that assert behaviour. If a branch is unreachable, delete it.

## Does the test actually hold?

A test that passes is not evidence; a test that fails when the behaviour breaks is. After writing a
test for a subtle rule, **break the production code and confirm it goes red**. Two examples from
the reference service: reverting the request-id filter's order from `-110` to `-100` fails two
`SecurityIT` tests, and removing the audience validator fails one. Both were verified that way.

## What not to test

Framework behaviour (that `@Valid` validates, that Spring routes), getters, mappings with no logic
beyond field copying — one round-trip test is enough — and anything asserted by matching a
framework's message text, which changes between versions. Never assert on a log line.
