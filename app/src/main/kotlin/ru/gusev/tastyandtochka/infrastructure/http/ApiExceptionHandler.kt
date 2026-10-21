package ru.gusev.tastyandtochka.infrastructure.http

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import ru.gusev.tastyandtochka.ordering.api.InvalidOrderException
import ru.gusev.tastyandtochka.ordering.api.OrderAmountTooLargeException
import ru.gusev.tastyandtochka.ordering.api.OrderNotFoundException
import ru.gusev.tastyandtochka.ordering.api.OrderVersionConflictException
import ru.gusev.tastyandtochka.ordering.api.ProductsNotFoundException
import ru.gusev.tastyandtochka.ordering.api.ProductsUnavailableException
import ru.gusev.tastyandtochka.ordering.domain.InvalidOrderStatusTransitionException
import java.util.UUID

@RestControllerAdvice
internal class ApiExceptionHandler : ResponseEntityExceptionHandler() {
    private val apiLogger = LoggerFactory.getLogger(ApiExceptionHandler::class.java)

    @ExceptionHandler(InvalidOrderException::class)
    fun invalidOrder(exception: InvalidOrderException, request: WebRequest): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.BAD_REQUEST, "INVALID_ORDER", exception.message ?: "Invalid order", request)

    @ExceptionHandler(ProductsNotFoundException::class)
    fun missingProducts(exception: ProductsNotFoundException, request: WebRequest): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.UNPROCESSABLE_CONTENT, "PRODUCTS_NOT_FOUND", "Some requested products do not exist", request, exception.productIds)

    @ExceptionHandler(ProductsUnavailableException::class)
    fun unavailableProducts(exception: ProductsUnavailableException, request: WebRequest): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.UNPROCESSABLE_CONTENT, "PRODUCTS_UNAVAILABLE", "Some requested products are unavailable", request, exception.productIds)

    @ExceptionHandler(OrderAmountTooLargeException::class)
    fun amountTooLarge(request: WebRequest): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.UNPROCESSABLE_CONTENT, "ORDER_AMOUNT_TOO_LARGE", "Order amount exceeds the supported range", request)

    @ExceptionHandler(OrderNotFoundException::class)
    fun missingOrder(exception: OrderNotFoundException, request: WebRequest): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", exception.message ?: "Order was not found", request)

    @ExceptionHandler(OrderVersionConflictException::class)
    fun versionConflict(exception: OrderVersionConflictException, request: WebRequest): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.CONFLICT, "ORDER_VERSION_CONFLICT", exception.message ?: "Order has changed", request)

    @ExceptionHandler(InvalidOrderStatusTransitionException::class)
    fun invalidTransition(exception: InvalidOrderStatusTransitionException, request: WebRequest): ResponseEntity<ApiErrorResponse> =
        error(HttpStatus.CONFLICT, "INVALID_ORDER_STATUS_TRANSITION", exception.message ?: "Order status transition is not allowed", request)

    @ExceptionHandler(Exception::class)
    fun unexpected(exception: Exception, request: WebRequest): ResponseEntity<ApiErrorResponse> {
        logFailure(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", request, exception)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(MediaType.APPLICATION_JSON)
            .body(ApiErrorResponse("INTERNAL_ERROR", "Could not process the request"))
    }

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val (code, defaultMessage) = when (statusCode.value()) {
            400 -> "INVALID_REQUEST" to "Request contains missing or invalid fields"
            404 -> "NOT_FOUND" to "Requested resource was not found"
            405 -> "METHOD_NOT_ALLOWED" to "HTTP method is not supported for this resource"
            406 -> "NOT_ACCEPTABLE" to "Requested response format is not supported"
            415 -> "UNSUPPORTED_MEDIA_TYPE" to "Request Content-Type is not supported"
            else -> if (statusCode.is5xxServerError) {
                "INTERNAL_ERROR" to "Could not process the request"
            } else {
                "INVALID_REQUEST" to "Request could not be processed"
            }
        }
        val message = if (ex is MethodArgumentNotValidException) {
            ex.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
                .ifBlank { defaultMessage }
        } else {
            defaultMessage
        }
        logFailure(statusCode, code, request, ex)
        return super.handleExceptionInternal(ex, ApiErrorResponse(code, message), headers, statusCode, request)
    }

    override fun createResponseEntity(
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> {
        val responseHeaders = HttpHeaders().apply {
            putAll(headers)
            contentType = MediaType.APPLICATION_JSON
        }
        return super.createResponseEntity(body, responseHeaders, statusCode, request)
    }

    private fun error(
        status: HttpStatus,
        code: String,
        message: String,
        request: WebRequest,
        productIds: Set<UUID>? = null,
    ): ResponseEntity<ApiErrorResponse> {
        logFailure(status, code, request)
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
            .body(ApiErrorResponse(code, message, productIds))
    }

    private fun logFailure(status: HttpStatusCode, code: String, request: WebRequest, exception: Exception? = null) {
        val servletRequest = (request as? ServletWebRequest)?.request
        val method = servletRequest?.method ?: "unknown"
        val path = servletRequest?.requestURI ?: "unknown"
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

internal data class ApiErrorResponse(val code: String, val message: String, val productIds: Set<UUID>? = null)
