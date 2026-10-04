package com.thridify.infrastructure.shopify

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.thridify.shared.exception.ApiException
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Component
class ShopifyHttpClient(private val mapper: ObjectMapper) {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build()

    fun post(url: String, headers: Map<String, String>, payload: Any): JsonNode {
        val body = if (payload is String) payload else mapper.writeValueAsString(payload)
        val request = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(20)).POST(HttpRequest.BodyPublishers.ofString(body))
        headers.forEach { (name, value) -> request.header(name, value) }
        val response = try {
            client.send(request.build(), HttpResponse.BodyHandlers.ofString())
        } catch (_: Exception) {
            throw ApiException(502, "Shopify is temporarily unavailable")
        }
        if (response.statusCode() !in 200..299) {
            val status = when (response.statusCode()) {
                400, 401 -> 401
                403 -> 403
                429 -> 503
                else -> 502
            }
            throw ApiException(status, if (status == 401) "Shopify authorization expired; reopen the app" else "Shopify request could not be completed")
        }
        val result = try {
            mapper.readTree(response.body())
        } catch (_: Exception) {
            throw ApiException(502, "Invalid Shopify response")
        }
        if (result.path("errors").isArray && !result.path("errors").isEmpty) throw ApiException(502, "Shopify could not complete the operation")
        return result
    }
}
