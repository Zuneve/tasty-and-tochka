package ru.gusev.tastyandtochka.infrastructure.http

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import ru.gusev.tastyandtochka.ordering.api.InvalidOrderException
import ru.gusev.tastyandtochka.ordering.api.OrderAmountTooLargeException
import ru.gusev.tastyandtochka.ordering.api.OrderNotFoundException
import ru.gusev.tastyandtochka.ordering.api.OrderStatusConflictException
import ru.gusev.tastyandtochka.ordering.api.ProductsNotFoundException
import ru.gusev.tastyandtochka.ordering.api.ProductsUnavailableException
import ru.gusev.tastyandtochka.ordering.domain.InvalidOrderStatusTransitionException
import java.util.UUID

@RestControllerAdvice
class ApiExceptionHandler {
    private val apiLogger = LoggerFactory.getLogger(ApiExceptionHandler::class.java)

    @ExceptionHandler(InvalidOrderException::class)
    fun invalidOrder(exception: InvalidOrderException, request: ServerWebExchange): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.BAD_REQUEST, "INVALID_ORDER", exception.message ?: "Invalid order", request)

    @ExceptionHandler(ProductsNotFoundException::class)
    fun missingProducts(exception: ProductsNotFoundException, request: ServerWebExchange): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.UNPROCESSABLE_CONTENT, "PRODUCTS_NOT_FOUND", "Some requested products do not exist", request, exception.productIds)

    @ExceptionHandler(ProductsUnavailableException::class)
    fun unavailableProducts(exception: ProductsUnavailableException, request: ServerWebExchange): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.UNPROCESSABLE_CONTENT, "PRODUCTS_UNAVAILABLE", "Some requested products are unavailable", request, exception.productIds)

    @ExceptionHandler(OrderAmountTooLargeException::class)
    fun amountTooLarge(request: ServerWebExchange): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.UNPROCESSABLE_CONTENT, "ORDER_AMOUNT_TOO_LARGE", "Order amount exceeds the supported range", request)

    @ExceptionHandler(OrderNotFoundException::class)
    fun missingOrder(exception: OrderNotFoundException, request: ServerWebExchange): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", exception.message ?: "Order was not found", request)

    @ExceptionHandler(OrderStatusConflictException::class)
    fun statusConflict(exception: OrderStatusConflictException, request: ServerWebExchange): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.CONFLICT, "ORDER_STATUS_CONFLICT", exception.message ?: "Order status has changed", request)

    @ExceptionHandler(InvalidOrderStatusTransitionException::class)
    fun invalidTransition(exception: InvalidOrderStatusTransitionException, request: ServerWebExchange): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.CONFLICT, "INVALID_ORDER_STATUS_TRANSITION", exception.message ?: "Order status transition is not allowed", request)

    @ExceptionHandler(Exception::class)
    fun unexpected(exception: Exception, request: ServerWebExchange): ResponseEntity<ApiErrorResponse> {
        logFailure(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", request, exception)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(MediaType.APPLICATION_JSON)
            .body(ApiErrorResponse("INTERNAL_ERROR", "Could not process the request"))
    }

    @ExceptionHandler(WebExchangeBindException::class)
    fun invalidFields(exception: WebExchangeBindException, request: ServerWebExchange): ResponseEntity<ApiErrorResponse> {
        val messages = ArrayList<String>()
        for (fieldError in exception.bindingResult.fieldErrors) {
            messages.add("${fieldError.field}: ${fieldError.defaultMessage}")
        }
        val message = if (messages.isEmpty()) {
            "Request contains missing or invalid fields"
        } else {
            messages.joinToString("; ")
        }
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message, request)
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun invalidRequest(exception: ResponseStatusException, request: ServerWebExchange): ResponseEntity<ApiErrorResponse> {
        val status = exception.statusCode
        val (code, message) = when (status.value()) {
            400 -> "INVALID_REQUEST" to "Request contains missing or invalid fields"
            404 -> "NOT_FOUND" to "Requested resource was not found"
            405 -> "METHOD_NOT_ALLOWED" to "HTTP method is not supported for this resource"
            406 -> "NOT_ACCEPTABLE" to "Requested response format is not supported"
            415 -> "UNSUPPORTED_MEDIA_TYPE" to "Request Content-Type is not supported"
            else -> if (status.is5xxServerError) {
                "INTERNAL_ERROR" to "Could not process the request"
            } else {
                "INVALID_REQUEST" to "Request could not be processed"
            }
        }
        logFailure(status, code, request, exception)
        return ResponseEntity.status(status)
            .headers(exception.headers)
            .contentType(MediaType.APPLICATION_JSON)
            .body(ApiErrorResponse(code, message))
    }

    private fun error(
        status: HttpStatus,
        code: String,
        message: String,
        request: ServerWebExchange,
        productIds: Set<UUID>? = null,
    ): ResponseEntity<ApiErrorResponse> {
        logFailure(status, code, request)
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
            .body(ApiErrorResponse(code, message, productIds))
    }

    private fun logFailure(status: HttpStatusCode, code: String, request: ServerWebExchange, exception: Exception? = null) {
        val method = request.request.method.name()
        val path = request.request.path.value()
        when {
            status.is5xxServerError -> apiLogger.error(
                "Request failed method={} path={} status={} code={}", method, path, status.value(), code, exception,
            )
            status.value() == 409 -> apiLogger.warn(
                "Request conflicted method={} path={} status={} code={}", method, path, status.value(), code,
            )
            else -> apiLogger.debug(
                "Request rejected method={} path={} status={} code={}", method, path, status.value(), code,
            )
        }
    }
}

data class ApiErrorResponse(val code: String, val message: String, val productIds: Set<UUID>? = null)
