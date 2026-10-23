package com.example.invoices.config

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder
import org.springframework.boot.http.client.HttpClientSettings
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

/**
 * The outbound HTTP client. Timeouts are explicit because the defaults are "wait forever", and one
 * slow upstream is enough to exhaust the request threads.
 */
@Configuration
class RestClientConfig {
    @Bean
    fun paymentGatewayRestClient(properties: InvoiceProperties): RestClient {
        val settings =
            HttpClientSettings
                .defaults()
                .withTimeouts(
                    properties.paymentGateway.connectTimeout,
                    properties.paymentGateway.readTimeout,
                )
        return RestClient
            .builder()
            .baseUrl(properties.paymentGateway.baseUrl)
            .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
            .build()
    }
}
