package com.omni3d.interfaces.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.omni3d.IntegrationTestBase
import com.omni3d.bearerToken
import com.omni3d.interfaces.rest.dto.SetWebhookRequest
import com.omni3d.registerAndGetToken
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put

class WebhookControllerTest : IntegrationTestBase() {

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var token: String

    @BeforeEach
    fun setup() {
        resetDatabase()
        token = mockMvc.registerAndGetToken(objectMapper, email = "webhook@example.com")
    }

    @Test
    fun `set webhook url creates a registration`() {
        val request = SetWebhookRequest(url = "https://example.com/callback")

        mockMvc.put("/dashboard/webhooks") {
            header("Authorization", bearerToken(token))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            jsonPath("$.url") { value("https://example.com/callback") }
            jsonPath("$.id") { exists() }
        }
    }

    @Test
    fun `get webhook returns current registration`() {
        mockMvc.put("/dashboard/webhooks") {
            header("Authorization", bearerToken(token))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(SetWebhookRequest(url = "https://example.com/cb"))
        }.andExpect { status { isOk() } }

        mockMvc.get("/dashboard/webhooks") {
            header("Authorization", bearerToken(token))
        }.andExpect {
            status { isOk() }
            jsonPath("$.url") { value("https://example.com/cb") }
        }
    }

    @Test
    fun `get webhook when none registered returns 404`() {
        mockMvc.get("/dashboard/webhooks") {
            header("Authorization", bearerToken(token))
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `delete webhook removes registration`() {
        mockMvc.put("/dashboard/webhooks") {
            header("Authorization", bearerToken(token))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(SetWebhookRequest(url = "https://example.com/cb"))
        }.andExpect { status { isOk() } }

        mockMvc.delete("/dashboard/webhooks") {
            header("Authorization", bearerToken(token))
        }.andExpect { status { isNoContent() } }

        mockMvc.get("/dashboard/webhooks") {
            header("Authorization", bearerToken(token))
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `webhook endpoints require auth`() {
        mockMvc.get("/dashboard/webhooks")
            .andExpect { status { isUnauthorized() } }

        mockMvc.put("/dashboard/webhooks") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"url":"https://x.com/cb"}"""
        }.andExpect { status { isUnauthorized() } }
    }
}
