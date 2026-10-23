package com.example.invoices.support

import com.example.invoices.repository.InvoiceRepository
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.client.RestTestClient
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * The base class for every integration test: the real application context, on a real HTTP port,
 * against a real Postgres, with every outbound HTTP dependency served by WireMock.
 *
 * The containers are started once in the companion's initialiser and shared by every subclass, so
 * the whole suite pays for one Postgres rather than one per test class. That is also why the test
 * task runs with a single fork - parallel forks would each start their own.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
abstract class AbstractIntegrationTest {
    @Autowired
    protected lateinit var client: RestTestClient

    @Autowired
    protected lateinit var invoices: InvoiceRepository

    @BeforeEach
    fun resetState() {
        invoices.deleteAll()
        wireMock.resetAll()
        stubJwks()
    }

    /** Auth0's JWKS endpoint, served locally so token validation is exercised for real. */
    private fun stubJwks() {
        wireMock.stubFor(
            get(JwtTestTokens.JWKS_PATH).willReturn(okJson(JwtTestTokens.jwksJson)),
        )
    }

    protected fun bearer(token: String = JwtTestTokens.token()): String = "Bearer $token"

    companion object {
        // Testcontainers 2.x dropped the self-referential generic: it is PostgreSQLContainer, not
        // PostgreSQLContainer<*>, and the module is org.testcontainers:testcontainers-postgresql.
        // @ServiceConnection hands the JDBC url, user and password to Boot, so no datasource
        // property is declared anywhere for tests.
        @JvmStatic
        @ServiceConnection
        val postgres: PostgreSQLContainer =
            PostgreSQLContainer("postgres:18-alpine")
                .withDatabaseName("invoices")
                .withUsername("invoices")
                .withPassword("test")

        @JvmStatic
        val wireMock: WireMockServer =
            WireMockServer(
                WireMockConfiguration
                    .options()
                    .dynamicPort()
                    // Boot 4's default HTTP client is the JDK one, which attempts an h2c upgrade on
                    // plain http://. WireMock's Jetty answers that with RST_STREAM, which surfaces
                    // as a mystifying "Stream cancelled" I/O error - and, because every client
                    // failure is a SystemException, as a 503 in the test. The real gateway is
                    // HTTPS, so forcing HTTP/1.1 here changes nothing about what is under test.
                    .http2PlainDisabled(true),
            )

        init {
            postgres.start()
            wireMock.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("invoices.auth.jwk-set-uri") { wireMock.baseUrl() + JwtTestTokens.JWKS_PATH }
            registry.add("invoices.payment-gateway.base-url") { wireMock.baseUrl() }
            registry.add("invoices.auth.issuer") { JwtTestTokens.ISSUER }
            registry.add("invoices.auth.audience") { JwtTestTokens.AUDIENCE }
        }
    }
}
