package com.example.invoices.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * The OpenAPI document served at /v3/api-docs and rendered at /swagger-ui.html.
 *
 * This document is a contract, not decoration: OpenApiExportIT writes it to contracts/api.yaml,
 * and the frontend generates its request/response types from that file. A hand-maintained copy on
 * either side is how the two drift apart.
 */
@Configuration
class OpenApiConfig {
    @Bean
    fun openApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Invoices API")
                    .version("v1")
                    .description(
                        "Errors are RFC 9457 problem+json. Each response documents the `type` URN " +
                            "it uses; clients branch on that, not on the message.",
                    ),
            )
            // A single relative server, declared explicitly. Left to itself springdoc writes the
            // absolute URL of whichever port the application happens to be listening on, which
            // would make the exported contracts/api.yaml differ on every run and turn a committed
            // contract into permanent diff noise.
            .servers(listOf(Server().url("/").description("This service")))
            .components(
                Components().addSecuritySchemes(
                    BEARER_SCHEME,
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Auth0 access token for this API's audience"),
                ),
            ).addSecurityItem(SecurityRequirement().addList(BEARER_SCHEME))

    private companion object {
        const val BEARER_SCHEME = "bearerAuth"
    }
}
