package com.thridify.infrastructure.provider.meshy

import com.thridify.domain.generation.GenerationProviderException
import com.thridify.domain.generation.GenerationProviderResult
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MeshyClientTest {
    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val images: MeshyInputImages = mockk()
    private val config = MeshyProperties(true, "secret", requestIntervalMs = 0)
    private val client = MeshyClient(config, images, builder.build())
    init {
        every { images.dataUri("one") } returns "data:image/png;base64,b25l"
        every { images.dataUri("two") } returns "data:image/png;base64,dHdv"
    }

    @Test
    fun `two views use multi-image with GLB USDZ and private data URI inputs`() {
        server.expect(requestTo("https://api.meshy.ai/openapi/v1/multi-image-to-3d"))
            .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", "Bearer secret"))
            .andExpect(content().json("""{"image_urls":["data:image/png;base64,b25l","data:image/png;base64,dHdv"],"ai_model":"meshy-7.1","should_texture":true,"target_formats":["glb","usdz"]}"""))
            .andRespond(withSuccess("""{"result":"opaque-task"}""", MediaType.APPLICATION_JSON))
        assertEquals("multi-image-to-3d:opaque-task", client.startGeneration(UUID.randomUUID(), "one", "two"))
        server.verify()
    }

    @Test
    fun `one view uses image-to-3d with a distinct opaque task handle`() {
        server.expect(requestTo("https://api.meshy.ai/openapi/v1/image-to-3d"))
            .andExpect(content().json("""{"image_url":"data:image/png;base64,b25l","ai_model":"meshy-7.1","should_texture":true,"target_formats":["glb","usdz"]}"""))
            .andRespond(withSuccess("""{"result":"single-task"}""", MediaType.APPLICATION_JSON))
        assertEquals("image-to-3d:single-task", client.startGeneration(UUID.randomUUID(), "one", null))
        server.verify()
    }

    @Test
    fun `successful status maps both formats without leaking provider statuses`() {
        server.expect(requestTo("https://api.meshy.ai/openapi/v1/multi-image-to-3d/task"))
            .andRespond(withSuccess("""{"id":"task","status":"SUCCEEDED","model_urls":{"glb":"https://assets.meshy.ai/a.glb","usdz":"https://assets.meshy.ai/a.usdz"}}""", MediaType.APPLICATION_JSON))
        assertEquals(GenerationProviderResult.Succeeded("https://assets.meshy.ai/a.glb", "https://assets.meshy.ai/a.usdz"), client.retrieveTask("multi-image-to-3d:task"))
        server.verify()
    }

    @Test
    fun `pending failure cancellation and incomplete completion map explicitly`() {
        for (status in listOf("PENDING", "IN_PROGRESS", "FAILED", "CANCELED", "SUCCEEDED")) {
            server.expect(requestTo("https://api.meshy.ai/openapi/v1/image-to-3d/task"))
                .andRespond(withSuccess("""{"id":"task","status":"$status"}""", MediaType.APPLICATION_JSON))
        }
        assertIs<GenerationProviderResult.Pending>(client.retrieveTask("image-to-3d:task"))
        assertIs<GenerationProviderResult.Pending>(client.retrieveTask("image-to-3d:task"))
        repeat(3) { assertIs<GenerationProviderResult.Failed>(client.retrieveTask("image-to-3d:task")) }
        server.verify()
    }

    @Test
    fun `foreign task malformed status and acknowledgement fail closed`() {
        server.expect(requestTo("https://api.meshy.ai/openapi/v1/image-to-3d/task"))
            .andRespond(withSuccess("""{"id":"foreign","status":"SUCCEEDED"}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("https://api.meshy.ai/openapi/v1/image-to-3d/task"))
            .andRespond(withSuccess("""{"id":"task","status":"UNKNOWN"}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("https://api.meshy.ai/openapi/v1/image-to-3d"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))
        assertThrows<IllegalStateException> { client.retrieveTask("image-to-3d:task") }
        assertThrows<GenerationProviderException> { client.retrieveTask("image-to-3d:task") }
        assertTrue(assertThrows<GenerationProviderException> { client.startGeneration(UUID.randomUUID(), "one", null) }.ambiguous)
        server.verify()
    }

    @Test
    fun `credits authorization and throttle rejection are sanitized and not ambiguous`() {
        for (status in listOf(HttpStatus.UNAUTHORIZED, HttpStatus.PAYMENT_REQUIRED, HttpStatus.TOO_MANY_REQUESTS)) {
            server.expect(requestTo("https://api.meshy.ai/openapi/v1/image-to-3d"))
                .andRespond(withStatus(status).body("sensitive upstream body"))
        }
        repeat(3) {
            val error = assertThrows<GenerationProviderException> { client.startGeneration(UUID.randomUUID(), "one", null) }
            assertEquals(false, error.ambiguous)
            assertEquals(false, error.message!!.contains("sensitive"))
        }
        server.verify()
    }

    @Test
    fun `privacy deletion is idempotent for expired tasks and retries in-progress conflicts`() {
        server.expect(requestTo("https://api.meshy.ai/openapi/v1/image-to-3d/task"))
            .andExpect(method(HttpMethod.DELETE)).andRespond(withStatus(HttpStatus.NOT_FOUND))
        server.expect(requestTo("https://api.meshy.ai/openapi/v1/image-to-3d/task"))
            .andRespond(withStatus(HttpStatus.CONFLICT))
        client.deleteTask("image-to-3d:task")
        assertThrows<GenerationProviderException> { client.deleteTask("image-to-3d:task") }
        server.verify()
    }
}
