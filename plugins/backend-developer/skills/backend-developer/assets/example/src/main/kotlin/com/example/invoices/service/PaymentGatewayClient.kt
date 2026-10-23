package com.example.invoices.service

import com.example.invoices.error.ErrorCode
import com.example.invoices.error.SystemException
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.util.UUID

/**
 * The outbound HTTP dependency, and the reason WireMock exists in the test suite.
 *
 * Every failure mode of the upstream becomes a [SystemException]: a dependency being down is this
 * service's problem to report, not something the caller can fix by changing their request.
 */
@Component
class PaymentGatewayClient(
    private val restClient: RestClient,
) {
    fun charge(
        invoiceId: UUID,
        amountCents: Long,
    ): String {
        val response =
            try {
                restClient
                    .post()
                    .uri("/charges")
                    .body(ChargeRequest(invoiceId.toString(), amountCents))
                    .retrieve()
                    .body(ChargeResponse::class.java)
            } catch (exception: RestClientException) {
                throw SystemException(
                    message = "Payment gateway call failed for invoice $invoiceId",
                    errorCode = ErrorCode.UPSTREAM_UNAVAILABLE,
                    cause = exception,
                )
            }

        return response?.chargeId
            ?: throw SystemException(
                message = "Payment gateway returned no charge id for invoice $invoiceId",
                errorCode = ErrorCode.UPSTREAM_UNAVAILABLE,
            )
    }

    internal data class ChargeRequest(
        val invoiceId: String,
        val amountCents: Long,
    )

    internal data class ChargeResponse(
        val chargeId: String?,
    )
}
