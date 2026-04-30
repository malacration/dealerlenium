package br.andrew.dealerlenium.infrastructure

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.openqa.selenium.WebDriverException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.server.ResponseStatusException
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime

@RestControllerAdvice
class ApiExceptionHandler {
    private val logger = LoggerFactory.getLogger(ApiExceptionHandler::class.java)

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(
        ex: ConstraintViolationException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> {
        return build(HttpStatus.BAD_REQUEST, ex.message ?: "Parametro invalido.", request.requestURI, ex)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> {
        val firstMessage = ex.bindingResult.fieldErrors.firstOrNull()?.defaultMessage
            ?: ex.bindingResult.globalErrors.firstOrNull()?.defaultMessage
        return build(HttpStatus.BAD_REQUEST, firstMessage ?: "Requisicao invalida.", request.requestURI, ex)
    }

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleHandlerMethodValidation(
        ex: HandlerMethodValidationException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> {
        val firstMessage = ex.allValidationResults
            .flatMap { it.resolvableErrors }
            .firstOrNull()
            ?.defaultMessage
        return build(HttpStatus.BAD_REQUEST, firstMessage ?: ex.message ?: "Requisicao invalida.", request.requestURI, ex)
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(
        ex: MethodArgumentTypeMismatchException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> {
        val message = "Parametro '${ex.name}' invalido."
        return build(HttpStatus.BAD_REQUEST, message, request.requestURI, ex)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(
        ex: IllegalArgumentException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> {
        return build(HttpStatus.BAD_REQUEST, ex.message ?: "Argumento invalido.", request.requestURI, ex)
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatus(
        ex: ResponseStatusException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> {
        val message = ex.reason ?: ex.message ?: "Erro na requisicao."
        return build(HttpStatus.valueOf(ex.statusCode.value()), message, request.requestURI, ex)
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(
        ex: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> {
        val dealerAccessMessage = resolveDealerAccessMessage(ex)
        return build(
            HttpStatus.INTERNAL_SERVER_ERROR,
            dealerAccessMessage ?: ex.message ?: "Erro interno.",
            request.requestURI,
            ex,
        )
    }

    private fun resolveDealerAccessMessage(ex: Throwable): String? {
        val currentChain = generateSequence(ex) { it.cause }

        return if (currentChain.any { cause ->
            cause is WebDriverException || cause.javaClass.name.startsWith("com.codeborne.selenide")
        }) {
            "Falha ao obter acesso ao dealernet"
        } else {
            null
        }
    }

    private fun build(
        status: HttpStatus,
        message: String,
        path: String,
        ex: Exception,
    ): ResponseEntity<ApiErrorResponse> {
        if (status.is5xxServerError) {
            logger.error("API error {} on {}: {}", status.value(), path, message, ex)
        } else {
            logger.warn("API error {} on {}: {}", status.value(), path, message, ex)
        }
        return ResponseEntity.status(status).body(
            ApiErrorResponse(
                status = status.value(),
                error = status.reasonPhrase,
                message = message,
                path = path,
                timestamp = OffsetDateTime.now().toString(),
            ),
        )
    }
}

data class ApiErrorResponse(
    val status: Int,
    val error: String,
    val message: String,
    val path: String,
    val timestamp: String,
)
