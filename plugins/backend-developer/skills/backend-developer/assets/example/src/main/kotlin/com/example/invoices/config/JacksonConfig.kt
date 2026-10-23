package com.example.invoices.config

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.cfg.DateTimeFeature

/**
 * JSON rules that the error contract depends on.
 *
 * `FAIL_ON_UNKNOWN_PROPERTIES` is on deliberately: silently dropping a misspelled field is how a
 * client ships a bug that looks like a server bug. GlobalExceptionHandler turns the resulting
 * failure into a 400 naming the property.
 *
 * Instants serialise as RFC 3339 strings rather than epoch numbers, so every timestamp this
 * service emits - including the one on a problem response - is readable and unambiguous.
 */
@Configuration
class JacksonConfig {
    @Bean
    fun jsonMapperBuilderCustomizer(): JsonMapperBuilderCustomizer =
        JsonMapperBuilderCustomizer { builder ->
            builder
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
}
