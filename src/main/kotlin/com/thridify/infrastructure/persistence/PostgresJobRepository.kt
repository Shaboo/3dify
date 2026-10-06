package com.thridify.infrastructure.persistence

import com.thridify.domain.job.JobEntity
import com.thridify.domain.job.JobRepository
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository("jobRepository")
class PostgresJobRepository(private val dsl: DSLContext) : JobRepository {

    private companion object {
        val TABLE = DSL.table("jobs")
        val API_KEYS = DSL.table("api_keys")

        // Qualified refs for SELECT / JOIN conditions
        val J_ID = DSL.field(DSL.name("jobs", "id"), UUID::class.java)
        val J_API_KEY_ID = DSL.field(DSL.name("jobs", "api_key_id"), UUID::class.java)
        val J_STATUS = DSL.field(DSL.name("jobs", "status"), String::class.java)
        val J_EXTERNAL_TASK_ID = DSL.field(DSL.name("jobs", "external_task_id"), String::class.java)
        val J_PRODUCT_ID = DSL.field("(SELECT a.product_id FROM shopify_model_attachments a WHERE a.job_id = jobs.id)", String::class.java)
        val J_ATTACHMENT_STATUS = DSL.field("(SELECT a.status FROM shopify_model_attachments a WHERE a.job_id = jobs.id)", String::class.java)
        val J_ATTACHMENT_ERROR = DSL.field("(SELECT a.error_message FROM shopify_model_attachments a WHERE a.job_id = jobs.id)", String::class.java)
        val J_INPUT_IMAGES = DSL.field(DSL.name("jobs", "input_images"), Array<String>::class.java)
        val J_INPUT_IMAGE_1 = DSL.field(DSL.name("jobs", "input_image_1"), String::class.java)
        val J_INPUT_IMAGE_2 = DSL.field(DSL.name("jobs", "input_image_2"), String::class.java)
        val J_OUTPUT_GLB_URL = DSL.field(DSL.name("jobs", "output_glb_url"), String::class.java)
        val J_OUTPUT_USDZ_URL = DSL.field(DSL.name("jobs", "output_usdz_url"), String::class.java)
        val J_WEBHOOK_URL = DSL.field(DSL.name("jobs", "webhook_url"), String::class.java)
        val J_ERROR_MESSAGE = DSL.field(DSL.name("jobs", "error_message"), String::class.java)
        val J_CREATED_AT = DSL.field(DSL.name("jobs", "created_at"), OffsetDateTime::class.java)
        val J_COMPLETED_AT = DSL.field(DSL.name("jobs", "completed_at"), OffsetDateTime::class.java)

        // api_keys column for JOIN
        val AK_ID = DSL.field(DSL.name("api_keys", "id"), UUID::class.java)
        val AK_USER_ID = DSL.field(DSL.name("api_keys", "created_by_user_id"), UUID::class.java)

        // All job columns (for SELECT)
        private val JOB_COLS = arrayOf(
            J_ID, J_API_KEY_ID, J_STATUS, J_EXTERNAL_TASK_ID,
            J_INPUT_IMAGE_1, J_INPUT_IMAGE_2, J_INPUT_IMAGES, J_PRODUCT_ID, J_ATTACHMENT_STATUS, J_ATTACHMENT_ERROR,
            J_OUTPUT_GLB_URL, J_OUTPUT_USDZ_URL, J_WEBHOOK_URL,
            J_ERROR_MESSAGE, J_CREATED_AT, J_COMPLETED_AT,
        )

        // Unqualified refs for UPDATE SET / INSERT (table prefix is invalid in DML column lists)
        private val COL_ID = DSL.field("id", UUID::class.java)
        private val COL_API_KEY_ID = DSL.field("api_key_id", UUID::class.java)
        private val COL_INPUT_IMAGE_1 = DSL.field("input_image_1", String::class.java)
        private val COL_INPUT_IMAGE_2 = DSL.field("input_image_2", String::class.java)
        private val COL_STATUS = DSL.field("status", String::class.java)
        private val COL_EXTERNAL_TASK_ID = DSL.field("external_task_id", String::class.java)
        private val COL_OUTPUT_GLB_URL = DSL.field("output_glb_url", String::class.java)
        private val COL_OUTPUT_USDZ_URL = DSL.field("output_usdz_url", String::class.java)
        private val COL_ERROR_MESSAGE = DSL.field("error_message", String::class.java)
        private val COL_COMPLETED_AT = DSL.field("completed_at", OffsetDateTime::class.java)
    }

    // Helper to cast a status string to the Postgres job_status ENUM
    private fun statusVal(s: String) = DSL.field("?::job_status", String::class.java, s)

    override fun insert(id: UUID, apiKeyId: UUID, imageKey1: String, imageKey2: String) = insert(id, apiKeyId, listOf(imageKey1, imageKey2))

    override fun insert(id: UUID, apiKeyId: UUID, imageKeys: List<String>) {
        dsl.insertInto(TABLE)
            .set(COL_ID, id)
            .set(COL_API_KEY_ID, apiKeyId)
            .set(DSL.field("workspace_id", UUID::class.java), DSL.field("(SELECT workspace_id FROM api_keys WHERE id = ?)", UUID::class.java, apiKeyId))
            .set(DSL.field("billing_scope_id", UUID::class.java), DSL.field("(SELECT billing_scope_id FROM api_keys WHERE id = ?)", UUID::class.java, apiKeyId))
            .set(COL_INPUT_IMAGE_1, imageKeys.first())
            .set(COL_INPUT_IMAGE_2, imageKeys.getOrElse(1) { imageKeys.first() })
            .set(DSL.field("input_images", Array<String>::class.java), imageKeys.toTypedArray())
            .execute()
    }

    override fun updateStatus(jobId: UUID, status: String) {
        dsl.update(TABLE)
            .set(COL_STATUS, statusVal(status))
            .where(COL_ID.eq(jobId))
            .execute()
    }

    override fun markSuccess(jobId: UUID, glbUrl: String, usdzUrl: String) {
        dsl.update(TABLE)
            .set(COL_STATUS, statusVal("SUCCESS"))
            .set(COL_OUTPUT_GLB_URL, glbUrl)
            .set(COL_OUTPUT_USDZ_URL, usdzUrl)
            .set(COL_COMPLETED_AT, OffsetDateTime.now())
            .where(COL_ID.eq(jobId))
            .execute()
    }

    override fun markFailed(jobId: UUID, errorMessage: String) {
        dsl.update(TABLE)
            .set(COL_STATUS, statusVal("FAILED"))
            .set(COL_ERROR_MESSAGE, errorMessage)
            .set(COL_COMPLETED_AT, OffsetDateTime.now())
            .where(COL_ID.eq(jobId))
            .execute()
    }

    override fun updateExternalTaskId(jobId: UUID, externalTaskId: String) {
        dsl.update(TABLE)
            .set(COL_EXTERNAL_TASK_ID, externalTaskId)
            .where(COL_ID.eq(jobId))
            .execute()
    }

    override fun lock(jobId: UUID): JobEntity? = dsl.select(*JOB_COLS).from(TABLE).where(J_ID.eq(jobId)).forUpdate().fetchOne()?.let(::toEntity)

    override fun findById(jobId: UUID): JobEntity? = dsl.select(*JOB_COLS).from(TABLE).where(J_ID.eq(jobId)).fetchOne()?.let(::toEntity)

    override fun findByExternalTaskId(externalTaskId: String): JobEntity? = dsl.select(*JOB_COLS).from(TABLE).where(J_EXTERNAL_TASK_ID.eq(externalTaskId)).fetchOne()?.let(::toEntity)

    override fun findAllByApiKeyId(apiKeyId: UUID): List<JobEntity> = dsl.select(*JOB_COLS)
        .from(TABLE)
        .where(J_API_KEY_ID.eq(apiKeyId))
        .orderBy(J_CREATED_AT.desc())
        .fetch()
        .map(::toEntity)

    override fun findAllByUserId(userId: UUID): List<JobEntity> = dsl.select(*JOB_COLS)
        .from(TABLE)
        .join(API_KEYS).on(J_API_KEY_ID.eq(AK_ID))
        .where(AK_USER_ID.eq(userId))
        .orderBy(J_CREATED_AT.desc())
        .fetch()
        .map(::toEntity)

    override fun findUserIdByJobId(jobId: UUID): UUID? = dsl.select(AK_USER_ID)
        .from(TABLE)
        .join(API_KEYS).on(J_API_KEY_ID.eq(AK_ID))
        .where(J_ID.eq(jobId))
        .fetchOne()
        ?.get(AK_USER_ID)

    private fun toEntity(r: org.jooq.Record) = JobEntity(
        id = r.get(J_ID)!!,
        apiKeyId = r.get(J_API_KEY_ID),
        status = r.get(J_STATUS)!!,
        externalTaskId = r.get(J_EXTERNAL_TASK_ID),
        inputImage1 = r.get(J_INPUT_IMAGE_1)!!,
        inputImage2 = r.get(J_INPUT_IMAGE_2)!!,
        outputGlbUrl = r.get(J_OUTPUT_GLB_URL),
        outputUsdzUrl = r.get(J_OUTPUT_USDZ_URL),
        webhookUrl = r.get(J_WEBHOOK_URL),
        errorMessage = r.get(J_ERROR_MESSAGE),
        createdAt = r.get(J_CREATED_AT)!!,
        completedAt = r.get(J_COMPLETED_AT),
        productId = r.get(J_PRODUCT_ID),
        attachmentStatus = r.get(J_ATTACHMENT_STATUS),
        attachmentError = r.get(J_ATTACHMENT_ERROR),
        inputImages = r.get(J_INPUT_IMAGES)?.toList() ?: listOf(r.get(J_INPUT_IMAGE_1)!!, r.get(J_INPUT_IMAGE_2)!!),
    )
}
