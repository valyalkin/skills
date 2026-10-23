package com.example.invoices.config

import com.example.invoices.support.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Exports the OpenAPI document to contracts/api.yaml and asserts it is complete enough to be a
 * contract.
 *
 * This is how the spec stays honest: it is generated from the running application, on every build,
 * rather than hand-maintained. The frontend generates its request/response types from the exported
 * file, so a controller change that is not reflected in the document shows up as a diff here - and
 * an endpoint missing its error responses fails the test outright.
 */
class OpenApiExportIT : AbstractIntegrationTest() {
    @Test
    fun `the document is exported to contracts api yaml`() {
        val yaml =
            client
                .get()
                .uri("/v3/api-docs.yaml")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String::class.java)
                .returnResult()
                .responseBody

        requireNotNull(yaml) { "the OpenAPI document was empty" }

        val target = Path.of("contracts", "api.yaml")
        Files.createDirectories(target.parent)
        Files.writeString(target, yaml)

        assertThat(target).exists()
        assertThat(yaml).contains("openapi: 3.1.0")
    }

    @Test
    fun `every invoice endpoint is documented, with its error responses`() {
        val json =
            client
                .get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()

        json
            .jsonPath("$.paths./api/v1/invoices.post.responses.201")
            .exists()
            .jsonPath("$.paths./api/v1/invoices.post.responses.400")
            .exists()
            .jsonPath("$.paths./api/v1/invoices.post.responses.409")
            .exists()
            .jsonPath("$.paths./api/v1/invoices/{id}.get.responses.404")
            .exists()
            .jsonPath("$.paths./api/v1/invoices/{id}/payment.post.responses.422")
            .exists()
            // The problem shape and the bearer scheme must both be part of the contract.
            .jsonPath("$.components.schemas.ProblemDetail")
            .exists()
            .jsonPath("$.components.securitySchemes.bearerAuth.scheme")
            .isEqualTo("bearer")
    }
}
