package ru.gusev.tastyandtochka.ordering.infrastructure.http

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import ru.gusev.tastyandtochka.ordering.application.InvalidOrderException
import ru.gusev.tastyandtochka.ordering.application.OrderAmountTooLargeException
import ru.gusev.tastyandtochka.ordering.application.OrderNotFoundException
import ru.gusev.tastyandtochka.ordering.application.ProductsNotFoundException
import ru.gusev.tastyandtochka.ordering.application.ProductsUnavailableException
import java.util.UUID

@RestControllerAdvice(assignableTypes = [OrderController::class])
internal class OrderExceptionHandler {
    private val logger = LoggerFactory.getLogger(OrderExceptionHandler::class.java)

    @ExceptionHandler(InvalidOrderException::class)
    fun invalidOrder(exception: InvalidOrderException): ResponseEntity<OrderErrorResponse> =
        error(HttpStatus.BAD_REQUEST, "INVALID_ORDER", exception.message!!)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(exception: MethodArgumentNotValidException): ResponseEntity<OrderErrorResponse> {
        val message = exception.bindingResult.fieldErrors.joinToString("; ") {
            "${it.field}: ${it.defaultMessage}"
        }
        return error(HttpStatus.BAD_REQUEST, "INVALID_ORDER", message)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class, MethodArgumentTypeMismatchException::class)
    fun malformedRequest(): ResponseEntity<OrderErrorResponse> =
        error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request contains missing or invalid fields")

    @ExceptionHandler(ProductsNotFoundException::class)
    fun missingProducts(exception: ProductsNotFoundException): ResponseEntity<OrderErrorResponse> =
        error(HttpStatus.UNPROCESSABLE_CONTENT, "PRODUCTS_NOT_FOUND", exception.message!!, exception.productIds)

    @ExceptionHandler(ProductsUnavailableException::class)
    fun unavailableProducts(exception: ProductsUnavailableException): ResponseEntity<OrderErrorResponse> =
        error(HttpStatus.UNPROCESSABLE_CONTENT, "PRODUCTS_UNAVAILABLE", exception.message!!, exception.productIds)

    @ExceptionHandler(OrderAmountTooLargeException::class)
    fun amountTooLarge(exception: OrderAmountTooLargeException): ResponseEntity<OrderErrorResponse> =
        error(HttpStatus.UNPROCESSABLE_CONTENT, "ORDER_AMOUNT_TOO_LARGE", exception.message!!)

    @ExceptionHandler(OrderNotFoundException::class)
    fun missingOrder(exception: OrderNotFoundException): ResponseEntity<OrderErrorResponse> =
        error(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", exception.message!!)

    @ExceptionHandler(Exception::class)
    fun unexpected(exception: Exception): ResponseEntity<OrderErrorResponse> {
        logger.error("Unexpected failure while processing an order request", exception)
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Could not process the order request")
    }

    private fun error(
        status: HttpStatus,
        code: String,
        message: String,
        productIds: Set<UUID>? = null,
    ): ResponseEntity<OrderErrorResponse> = ResponseEntity.status(status).body(OrderErrorResponse(code, message, productIds))
}

internal data class OrderErrorResponse(val code: String, val message: String, val productIds: Set<UUID>?)
