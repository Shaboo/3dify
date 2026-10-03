package com.omni3d.api.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.omni3d.api.IntegrationTestBase
import com.omni3d.api.model.dto.LoginRequest
import com.omni3d.api.model.dto.RegisterRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthControllerTest : IntegrationTestBase() {

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setup() = resetDatabase()

    // -------- Register --------

    @Test
    fun `register creates user and returns token`() {
        val body = RegisterRequest(email = "test@example.com", password = "password123", name = "Test User")

        val result = mockMvc.post("/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(body)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.token") { exists() }
            jsonPath("$.email") { value("test@example.com") }
            jsonPath("$.isAdmin") { value(false) }
        }.andReturn()

        val response = objectMapper.readTree(result.response.contentAsString)
        assertNotNull(response.get("token").asText())
    }

    @Test
    fun `register with duplicate email returns 409`() {
        val body = RegisterRequest(email = "dup@example.com", password = "pass", name = "Dup")
        val json = objectMapper.writeValueAsString(body)

        // First registration
        mockMvc.post("/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = json
        }.andExpect { status { isCreated() } }

        // Second registration with same email
        mockMvc.post("/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = json
        }.andExpect { status { isConflict() } }
    }

    // -------- Login --------

    @Test
    fun `login with valid credentials returns token`() {
        // Register first
        val regBody = RegisterRequest(email = "login@example.com", password = "secret", name = "Login User")
        mockMvc.post("/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(regBody)
        }.andExpect { status { isCreated() } }

        // Now login
        val loginBody = LoginRequest(email = "login@example.com", password = "secret")
        val result = mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(loginBody)
        }.andExpect {
            status { isOk() }
            jsonPath("$.token") { exists() }
            jsonPath("$.email") { value("login@example.com") }
        }.andReturn()

        val response = objectMapper.readTree(result.response.contentAsString)
        assertTrue(response.get("token").asText().isNotBlank())
    }

    @Test
    fun `login with wrong password returns 401`() {
        // Register
        mockMvc.post("/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                RegisterRequest(email = "wrong@example.com", password = "correct", name = "User")
            )
        }.andExpect { status { isCreated() } }

        // Login with wrong password
        mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                LoginRequest(email = "wrong@example.com", password = "wrongpassword")
            )
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `login with non-existent email returns 401`() {
        mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                LoginRequest(email = "ghost@example.com", password = "pass")
            )
        }.andExpect { status { isUnauthorized() } }
    }
}
