package com.thridify.infrastructure.provider.runpod

import com.thridify.domain.generation.GenerationProviderException
import com.thridify.domain.generation.GenerationProviderResult
import com.thridify.shared.metrics.AppMetrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.util.UUID
import kotlin.test.assertEquals

class RunPodClientTest {
    @Test
    fun `real async dispatch sends managed output prefix and returns the provider task identifier`() {
        val metrics = AppMetrics(SimpleMeterRegistry())
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val testClient = RunPodClient("https://api.runpod.ai/v2/endpoint/run", "secret", "https://backend.example/internal/webhooks/runpod", metrics, true, builder.build())
        val id = UUID.randomUUID()
        server.expect(requestTo("https://api.runpod.ai/v2/endpoint/run"))
            .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", "Bearer secret"))
            .andExpect(content().json("""{"input":{"jobId":"$id","image1":"one","image2":"two","outputPrefix":"outputs/$id/"},"webhook":"https://backend.example/internal/webhooks/runpod/$id"}"""))
            .andRespond(withSuccess("""{"id":"external-task","status":"IN_QUEUE"}""", MediaType.APPLICATION_JSON))
        assertEquals("external-task", testClient.startGeneration(id, "one", "two"))
        assertEquals(1.0, metrics.runpodDispatched.count())
        server.verify()
    }

    @Test
    fun `malformed provider acknowledgments do not count as successful dispatch`() {
        for (body in listOf("{}", "{\"id\":\"\",\"status\":\"IN_QUEUE\"}", "{\"id\":\"task\",\"status\":\"UNKNOWN\"}")) {
            val metrics = AppMetrics(SimpleMeterRegistry())
            val builder = RestClient.builder()
            val server = MockRestServiceServer.bindTo(builder).build()
            val client = RunPodClient("https://api.runpod.ai/v2/endpoint/run", "secret", "https://backend.example/internal/webhooks/runpod", metrics, true, builder.build())
            server.expect(requestTo("https://api.runpod.ai/v2/endpoint/run")).andRespond(withSuccess(body, MediaType.APPLICATION_JSON))
            assertThrows<GenerationProviderException> { client.startGeneration(UUID.randomUUID(), "one", "two") }
            assertEquals(0.0, metrics.runpodDispatched.count())
            assertEquals(1.0, metrics.runpodDispatchErrors.count())
            server.verify()
        }
    }

    @Test
    fun `RunPod retrieval participates in provider-neutral completion and expired privacy cleanup`() {
        val metrics = AppMetrics(SimpleMeterRegistry())
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val client = RunPodClient("https://api.runpod.ai/v2/endpoint/run", "secret", "https://backend.example/internal/webhooks/runpod", metrics, true, builder.build())
        server.expect(requestTo("https://api.runpod.ai/v2/endpoint/status/task"))
            .andRespond(withSuccess("""{"id":"task","status":"COMPLETED","output":{"glb":"https://assets/model.glb","usdz":"https://assets/model.usdz"}}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("https://api.runpod.ai/v2/endpoint/status/expired"))
            .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(org.springframework.http.HttpStatus.NOT_FOUND))
        assertEquals(GenerationProviderResult.Succeeded("https://assets/model.glb", "https://assets/model.usdz"), client.retrieveTask("task"))
        client.deleteTask("expired")
        server.verify()
    }

    @Test
    fun `disabled generation fails without inventing a task`() {
        val metrics = AppMetrics(SimpleMeterRegistry())
        val client = RunPodClient("", "", "", metrics)
        assertThrows<GenerationProviderException> { client.startGeneration(UUID.randomUUID(), "one", "two") }
        assertEquals(0.0, metrics.runpodDispatched.count())
        assertEquals(1.0, metrics.runpodDispatchErrors.count())
    }
}
