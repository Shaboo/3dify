package com.`3dify`.interfaces.webhook

import com.`3dify`.IntegrationTestBase
import com.`3dify`.domain.job.JobHistoryRepository
import com.`3dify`.domain.job.JobRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@SpringBootTest
class ProviderWebhookControllerTest : IntegrationTestBase() {

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jobHistory: JobHistoryRepository

    @Autowired
    private lateinit var jobRepository: JobRepository

    // We can spy on or mock the WebhookService if we strictly want to verify
    // the outgoing REST calls, but for this test we'll just verify the DB state
    // changes correctly when the webhook hits.

    private fun seedJob(apiKeyId: UUID): UUID {
        val id = UUID.randomUUID()
        jobRepository.insert(id, apiKeyId, "input1", "input2")
        jobHistory.insert(id, "PENDING", "Job created")
        return id
    }

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
        val jobId = seedJob(apiKeyId)
        jobRepository.updateStatus(jobId, "PROCESSING")

        // Give it an external task ID (simulate what TaskWorker does in the real flow)
        val externalTaskId = "runpod-12345"
        jobRepository.updateExternalTaskId(jobId, externalTaskId)

        // Act: Send the webhook from the mock provider
        val payload = ProviderWebhookController.RunPodWebhookPayload(
            id = externalTaskId,
            status = "COMPLETED",
            output = ProviderWebhookController.RunPodOutput(
                glb = "https://r2.com/model.glb",
                usdz = "https://r2.com/model.usdz",
            ),
        )

        mockMvc.perform(
            post("/internal/webhooks/runpod/$jobId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)),
        ).andExpect(status().isOk)

        // Assert: Job is updated
        val updatedJob = jobRepository.findById(jobId)!!
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

        val jobId = seedJob(apiKeyId)
        jobRepository.updateStatus(jobId, "PROCESSING")
        val externalTaskId = "runpod-fail-99"
        jobRepository.updateExternalTaskId(jobId, externalTaskId)

        val payload = ProviderWebhookController.RunPodWebhookPayload(
            id = externalTaskId,
            status = "FAILED",
            output = null,
        )

        mockMvc.perform(
            post("/internal/webhooks/runpod/$jobId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)),
        ).andExpect(status().isOk)

        val updatedJob = jobRepository.findById(jobId)!!
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

        val jobId = seedJob(apiKeyId)
        jobRepository.updateStatus(jobId, "PROCESSING")
        jobRepository.updateExternalTaskId(jobId, "real-runpod-id")

        val payload = ProviderWebhookController.RunPodWebhookPayload(
            id = "fake-hacker-id", // Mismatch!
            status = "COMPLETED",
            output = null,
        )

        mockMvc.perform(
            post("/internal/webhooks/runpod/$jobId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)),
        ).andExpect(status().isBadRequest)
    }
}
