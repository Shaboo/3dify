package com.thridify.interfaces.rest.exceptions

import com.fasterxml.jackson.databind.ObjectMapper
import com.thridify.shared.exception.ApiException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

@RestControllerAdvice
class GlobalExceptionHandler(private val objectMapper: ObjectMapper) {
    private val log = LoggerFactory.getLogger(javaClass)

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
    fun handleApiException(ex: ApiException, response: HttpServletResponse, request: HttpServletRequest) {
        if (ex.statusCode == 401 && request.requestURI.startsWith("/shopify/api/")) response.setHeader("X-Shopify-Retry-Invalid-Session-Request", "1")
        writeError(response, HttpStatus.valueOf(ex.statusCode), ex.message)
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException::class)
    fun handleUploadLimit(ex: org.springframework.web.multipart.MaxUploadSizeExceededException, response: HttpServletResponse) = writeError(response, HttpStatus.PAYLOAD_TOO_LARGE, "Each image must be up to 20 MB")

    @ExceptionHandler(
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException::class,
        org.springframework.web.bind.MissingRequestHeaderException::class,
        org.springframework.web.multipart.support.MissingServletRequestPartException::class,
    )
    fun handleMalformedRequest(ex: Exception, response: HttpServletResponse) = writeError(response, HttpStatus.BAD_REQUEST, "Required request fields are missing or invalid")

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception, response: HttpServletResponse) {
        log.error("http_request_failed error_type={}", ex.javaClass.simpleName)
        writeError(response, HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error")
    }
}
