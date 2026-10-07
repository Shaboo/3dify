package com.thridify.interfaces.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.thridify.IntegrationTestBase
import com.thridify.bearerToken
import com.thridify.registerAndGetToken
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.get

class GenerationOptionsControllerTest : IntegrationTestBase() {
    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `signed in user can read provider photo limits`() {
        resetDatabase()
        val token = mockMvc.registerAndGetToken(objectMapper, email = "generation-options@example.com")
        mockMvc.get("/dashboard/generation-options") {
            header("Authorization", bearerToken(token))
        }.andExpect {
            status { isOk() }
            jsonPath("$.provider") { isNotEmpty() }
            jsonPath("$.minImages") { value(1) }
        }
    }

    @Test
    fun `anonymous user cannot read generation options`() {
        mockMvc.get("/dashboard/generation-options").andExpect { status { isUnauthorized() } }
    }
}
