package com.example.invoices.error

import org.springframework.http.HttpStatus

/**
 * A request the caller can fix: the client is wrong, not the service. Always a 4xx.
 *
 * Throw it from `service/` only. Controllers do not decide status codes and repositories do not
 * know about HTTP; this is the single channel through which business rules become responses.
 */
open class BusinessException(
    val errorCode: ErrorCode,
    override val message: String,
    val violations: List<FieldViolation> = emptyList(),
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    init {
        require(errorCode.status.is4xxClientError) {
            "BusinessException needs a 4xx ErrorCode, but $errorCode is ${errorCode.status}"
        }
    }
}

/** The addressed resource does not exist. 404. */
class NotFoundException(
    errorCode: ErrorCode,
    message: String,
    cause: Throwable? = null,
) : BusinessException(errorCode, message, emptyList(), cause) {
    init {
        require(errorCode.status == HttpStatus.NOT_FOUND) {
            "NotFoundException needs a 404 ErrorCode, but $errorCode is ${errorCode.status}"
        }
    }
}

/** The resource already exists, or the write would break a uniqueness rule. 409. */
class DuplicateException(
    errorCode: ErrorCode,
    message: String,
    cause: Throwable? = null,
) : BusinessException(errorCode, message, emptyList(), cause) {
    init {
        require(errorCode.status == HttpStatus.CONFLICT) {
            "DuplicateException needs a 409 ErrorCode, but $errorCode is ${errorCode.status}"
        }
    }
}

/**
 * The request was well formed and the resource exists, but a business rule refuses it - paying an
 * already-paid invoice, for instance. 422, which is what distinguishes "I cannot parse this" (400)
 * from "I understood you and the answer is no".
 */
class UnprocessableEntityException(
    errorCode: ErrorCode,
    message: String,
    violations: List<FieldViolation> = emptyList(),
    cause: Throwable? = null,
) : BusinessException(errorCode, message, violations, cause) {
    init {
        require(errorCode.status == HttpStatus.UNPROCESSABLE_ENTITY) {
            "UnprocessableEntityException needs a 422 ErrorCode, but $errorCode is ${errorCode.status}"
        }
    }
}
