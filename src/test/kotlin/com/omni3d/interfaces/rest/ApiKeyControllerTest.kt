package com.omni3d.interfaces.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.omni3d.IntegrationTestBase
import com.omni3d.bearerToken
import com.omni3d.interfaces.rest.dto.CreateApiKeyRequest
import com.omni3d.registerAndGetToken
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

class ApiKeyControllerTest : IntegrationTestBase() {

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var token: String

    @BeforeEach
    fun setup() {
        resetDatabase()
        seedDefaultPlans()
        token = mockMvc.registerAndGetToken(objectMapper, email = "apikey@example.com")
    }

    @Test
    fun `create API key returns key and prefix`() {
        val request = CreateApiKeyRequest(label = "my key", planName = "free")

        val result = mockMvc.post("/dashboard/api-keys") {
            header("Authorization", bearerToken(token))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.key") { exists() }
            jsonPath("$.planName") { value("free") }
            jsonPath("$.label") { value("my key") }
        }.andReturn()

        val response = objectMapper.readTree(result.response.contentAsString)
        assert(response.get("key").asText().startsWith("omni_pk_"))
    }

    @Test
    fun `list API keys returns created keys`() {
        // Create a key
        mockMvc.post("/dashboard/api-keys") {
            header("Authorization", bearerToken(token))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(CreateApiKeyRequest(label = "k1", planName = "free"))
        }.andExpect { status { isCreated() } }

        mockMvc.get("/dashboard/api-keys") {
            header("Authorization", bearerToken(token))
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].label") { value("k1") }
            jsonPath("$[0].isActive") { value(true) }
        }
    }

    @Test
    fun `revoke API key marks it inactive`() {
        // Create
        val createResult = mockMvc.post("/dashboard/api-keys") {
            header("Authorization", bearerToken(token))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(CreateApiKeyRequest(label = "to-revoke", planName = "free"))
        }.andExpect { status { isCreated() } }.andReturn()

        val keyId = objectMapper.readTree(createResult.response.contentAsString).get("id").asText()

        // Revoke
        mockMvc.delete("/dashboard/api-keys/$keyId") {
            header("Authorization", bearerToken(token))
        }.andExpect { status { isNoContent() } }

        // List — key should be inactive
        mockMvc.get("/dashboard/api-keys") {
            header("Authorization", bearerToken(token))
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].isActive") { value(false) }
        }
    }

    @Test
    fun `revoke non-existent key returns 404`() {
        mockMvc.delete("/dashboard/api-keys/${UUID.randomUUID()}") {
            header("Authorization", bearerToken(token))
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `create API key without auth returns 401`() {
        mockMvc.post("/dashboard/api-keys") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(CreateApiKeyRequest(planName = "free"))
        }.andExpect { status { isUnauthorized() } }
    }
}
