package com.`3dify`

import com.`3dify`.interfaces.rest.dto.RegisterRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

/** Registers a user and returns the JWT token for use in authenticated test requests. */
fun MockMvc.registerAndGetToken(
    objectMapper: ObjectMapper,
    email: String = "user@example.com",
    password: String = "password123",
    name: String = "Test User",
): String {
    val result = this.post("/auth/register") {
        contentType = MediaType.APPLICATION_JSON
        content = objectMapper.writeValueAsString(
            RegisterRequest(email = email, password = password, name = name),
        )
    }.andExpect { status { isCreated() } }.andReturn()

    return objectMapper.readTree(result.response.contentAsString).get("token").asText()
}

/** Returns an Authorization header value. */
fun bearerToken(token: String) = "Bearer $token"
