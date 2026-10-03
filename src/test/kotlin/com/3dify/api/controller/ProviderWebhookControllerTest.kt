package com.omni3d.api.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.omni3d.api.IntegrationTestBase
import com.omni3d.api.repository.JobRepository
import com.omni3d.api.service.JobService
import com.omni3d.api.service.WebhookService
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.*

@SpringBootTest
class ProviderWebhookControllerTest : IntegrationTestBase() {

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jobService: JobService

    @Autowired
    private lateinit var jobRepository: JobRepository

    // We can spy on or mock the WebhookService if we strictly want to verify
    // the outgoing REST calls, but for this test we'll just verify the DB state
    // changes correctly when the webhook hits.
    
    @BeforeEach
    fun setup() {
        resetDatabase()
        seedDefaultPlans()
    }

    @Test
    fun `successful webhook updates job status to SUCCESS and sets output URLs`() {
        // Setup: Create a user, plan, api key, and job
        val userId = UUID.randomUUID()
        dsl.execute("INSERT INTO users (id, email, password_hash) VALUES ('$userId', 'test@test.com', 'hash')")
        
        val planId = dsl.fetchOne("SELECT id FROM plans WHERE name = 'pro'")?.get("id") as UUID
        val apiKeyId = UUID.randomUUID()
        dsl.execute("INSERT INTO api_keys (id, user_id, plan_id, key_hash, key_prefix) VALUES ('$apiKeyId', '$userId', '$planId', 'hash', 'prefix')")

        // Create the job and mark it processing
        val jobId = jobService.createJob(apiKeyId, "input1", "input2")
        jobService.markProcessing(jobId)

        // Give it an external task ID (simulate what TaskWorker does in the real flow)
        val externalTaskId = "runpod-12345"
        jobService.updateExternalTaskId(jobId, externalTaskId)

        // Act: Send the webhook from the mock provider
        val payload = ProviderWebhookController.RunPodWebhookPayload(
            id = externalTaskId,
            status = "COMPLETED",
            output = ProviderWebhookController.RunPodOutput(
                glb = "https://r2.com/model.glb",
                usdz = "https://r2.com/model.usdz"
            )
        )

        mockMvc.perform(
            post("/internal/webhooks/runpod/$jobId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        ).andExpect(status().isOk)

        // Assert: Job is updated
        val updatedJob = jobService.getJobById(jobId)
        assert(updatedJob.status == "SUCCESS")
        assert(updatedJob.outputGlbUrl == "https://r2.com/model.glb")
        assert(updatedJob.outputUsdzUrl == "https://r2.com/model.usdz")
        assert(updatedJob.completedAt != null)
    }

    @Test
    fun `failed webhook updates job status to FAILED`() {
        val userId = UUID.randomUUID()
        dsl.execute("INSERT INTO users (id, email, password_hash) VALUES ('$userId', 'test2@test.com', 'hash')")
        val planId = dsl.fetchOne("SELECT id FROM plans WHERE name = 'pro'")?.get("id") as UUID
        val apiKeyId = UUID.randomUUID()
        dsl.execute("INSERT INTO api_keys (id, user_id, plan_id, key_hash, key_prefix) VALUES ('$apiKeyId', '$userId', '$planId', 'hash', 'prefix')")

        val jobId = jobService.createJob(apiKeyId, "input1", "input2")
        jobService.markProcessing(jobId)
        val externalTaskId = "runpod-fail-99"
        jobService.updateExternalTaskId(jobId, externalTaskId)

        val payload = ProviderWebhookController.RunPodWebhookPayload(
            id = externalTaskId,
            status = "FAILED",
            output = null
        )

        mockMvc.perform(
            post("/internal/webhooks/runpod/$jobId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        ).andExpect(status().isOk)

        val updatedJob = jobService.getJobById(jobId)
        assert(updatedJob.status == "FAILED")
        assert(updatedJob.errorMessage == "GPU Provider reported failure")
    }

    @Test
    fun `webhook with mismatched external task ID is rejected as BAD REQUEST`() {
        val userId = UUID.randomUUID()
        dsl.execute("INSERT INTO users (id, email, password_hash) VALUES ('$userId', 'test3@test.com', 'hash')")
        val planId = dsl.fetchOne("SELECT id FROM plans WHERE name = 'pro'")?.get("id") as UUID
        val apiKeyId = UUID.randomUUID()
        dsl.execute("INSERT INTO api_keys (id, user_id, plan_id, key_hash, key_prefix) VALUES ('$apiKeyId', '$userId', '$planId', 'hash', 'prefix')")

        val jobId = jobService.createJob(apiKeyId, "input1", "input2")
        jobService.markProcessing(jobId)
        jobService.updateExternalTaskId(jobId, "real-runpod-id")

        val payload = ProviderWebhookController.RunPodWebhookPayload(
            id = "fake-hacker-id", // Mismatch!
            status = "COMPLETED",
            output = null
        )

        mockMvc.perform(
            post("/internal/webhooks/runpod/$jobId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        ).andExpect(status().isBadRequest)
    }
}
