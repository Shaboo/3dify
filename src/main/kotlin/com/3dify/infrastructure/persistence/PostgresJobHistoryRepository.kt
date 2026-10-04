package com.`3dify`.infrastructure.persistence

import com.`3dify`.domain.job.JobHistoryEntity
import com.`3dify`.domain.job.JobHistoryRepository
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository("jobHistoryRepository")
class PostgresJobHistoryRepository(private val dsl: DSLContext) : JobHistoryRepository {

    private companion object {
        val TABLE = DSL.table("job_history")
        val ID = DSL.field("id", UUID::class.java)
        val JOB_ID = DSL.field("job_id", UUID::class.java)
        val STATUS = DSL.field("status", String::class.java)
        val DETAILS = DSL.field("details", String::class.java)
        val CREATED_AT = DSL.field("created_at", OffsetDateTime::class.java)
    }

    override fun insert(jobId: UUID, status: String, details: String?) {
        dsl.insertInto(TABLE)
            .set(ID, UUID.randomUUID())
            .set(JOB_ID, jobId)
            .set(STATUS, status)
            .set(DETAILS, details)
            .execute()
    }

    override fun findAllByJobId(jobId: UUID): List<JobHistoryEntity> = dsl.select(ID, JOB_ID, STATUS, DETAILS, CREATED_AT)
        .from(TABLE)
        .where(JOB_ID.eq(jobId))
        .orderBy(CREATED_AT.asc())
        .fetch()
        .map { r ->
            JobHistoryEntity(
                id = r.get(ID)!!,
                jobId = r.get(JOB_ID)!!,
                status = r.get(STATUS)!!,
                details = r.get(DETAILS),
                createdAt = r.get(CREATED_AT)!!,
            )
        }
}
