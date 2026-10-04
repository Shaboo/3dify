package com.thridify.interfaces.rest.exceptions

import com.fasterxml.jackson.databind.ObjectMapper
import com.thridify.shared.exception.ApiException
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

@RestControllerAdvice
class GlobalExceptionHandler(private val objectMapper: ObjectMapper) {

    data class ErrorBody(
        val error: String,
        val message: String,
        val timestamp: String = Instant.now().toString(),
    )

    /**
     * Write the error JSON directly to the response instead of returning a ResponseEntity.
     * This prevents Spring Security's ExceptionTranslationFilter from intercepting 4xx/5xx
     * responses returned from @RestControllerAdvice and rerouting them through /error.
     */
    private fun writeError(response: HttpServletResponse, status: HttpStatus, message: String) {
        response.status = status.value()
        response.contentType = "application/json"
        objectMapper.writeValue(
            response.writer,
            ErrorBody(error = status.reasonPhrase, message = message),
        )
    }

    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException, response: HttpServletResponse) = writeError(response, HttpStatus.valueOf(ex.statusCode), ex.message)

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception, response: HttpServletResponse) = writeError(response, HttpStatus.INTERNAL_SERVER_ERROR, ex.message ?: "Unexpected error")
}
