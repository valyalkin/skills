package com.example.invoices.error

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The exception types police their own status, so a mismatch is a failure at the throw site rather
 * than a wrong status code discovered by a client.
 */
class ExceptionHierarchyTest {
    @Test
    fun `NotFoundException rejects an ErrorCode that is not a 404`() {
        assertThatThrownBy { NotFoundException(ErrorCode.DUPLICATE_INVOICE, "wrong") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("needs a 404")
    }

    @Test
    fun `DuplicateException rejects an ErrorCode that is not a 409`() {
        assertThatThrownBy { DuplicateException(ErrorCode.INVOICE_NOT_FOUND, "wrong") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("needs a 409")
    }

    @Test
    fun `UnprocessableEntityException rejects an ErrorCode that is not a 422`() {
        assertThatThrownBy { UnprocessableEntityException(ErrorCode.VALIDATION_FAILED, "wrong") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("needs a 422")
    }

    @Test
    fun `BusinessException rejects a 5xx ErrorCode`() {
        assertThatThrownBy { BusinessException(ErrorCode.INTERNAL_ERROR, "wrong") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("needs a 4xx")
    }

    @Test
    fun `SystemException rejects a 4xx ErrorCode`() {
        assertThatThrownBy { SystemException("wrong", ErrorCode.VALIDATION_FAILED) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("needs a 5xx")
    }

    @Test
    fun `the subclasses accept their own status and carry the code`() {
        assertThat(NotFoundException(ErrorCode.INVOICE_NOT_FOUND, "x").errorCode)
            .isEqualTo(ErrorCode.INVOICE_NOT_FOUND)
        assertThat(DuplicateException(ErrorCode.DUPLICATE_INVOICE, "x").errorCode)
            .isEqualTo(ErrorCode.DUPLICATE_INVOICE)
        assertThat(UnprocessableEntityException(ErrorCode.INVOICE_NOT_PAYABLE, "x").errorCode)
            .isEqualTo(ErrorCode.INVOICE_NOT_PAYABLE)
        assertThat(SystemException("x").errorCode).isEqualTo(ErrorCode.INTERNAL_ERROR)
    }

    @Test
    fun `violations ride along on the exception`() {
        val violation = FieldViolation("#/amount", "amount", "Min", "must be greater than 0")

        val exception =
            UnprocessableEntityException(
                ErrorCode.INVOICE_NOT_PAYABLE,
                "nope",
                violations = listOf(violation),
            )

        assertThat(exception.violations).containsExactly(violation)
    }

    /**
     * Every code must declare a type URN that is unique and namespaced, because clients branch on
     * it. A copy-pasted URN would make two different errors indistinguishable.
     */
    @ParameterizedTest
    @EnumSource(ErrorCode::class)
    fun `every error code has a namespaced type and a title`(errorCode: ErrorCode) {
        assertThat(errorCode.type.toString()).startsWith("urn:problem:invoices:")
        assertThat(errorCode.title).isNotBlank()
    }

    @Test
    fun `type URNs are unique across the catalogue`() {
        val types = ErrorCode.entries.map { it.type }

        assertThat(types).doesNotHaveDuplicates()
    }
}
