package com.example.invoices.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * Time is a dependency. Injecting a Clock is what lets InvoiceServiceTest assert on createdAt
 * without sleeping or matching "roughly now".
 */
@Configuration
class ClockConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
