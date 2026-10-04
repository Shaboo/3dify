package com.`3dify`.infrastructure.persistence

import com.`3dify`.domain.outbox.OutboxMessageEntity
import com.`3dify`.domain.outbox.OutboxRepository
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository("outboxRepository")
class PostgresOutboxRepository(private val dsl: DSLContext) : OutboxRepository {

    private companion object {
        val TABLE = DSL.table("outbox_messages")
        val ID = DSL.field(DSL.name("outbox_messages", "id"), UUID::class.java)
        val AGGREGATE_TYPE = DSL.field(DSL.name("outbox_messages", "aggregate_type"), String::class.java)
        val AGGREGATE_ID = DSL.field(DSL.name("outbox_messages", "aggregate_id"), UUID::class.java)
        val PAYLOAD = DSL.field(DSL.name("outbox_messages", "payload"), String::class.java)
        val CREATED_AT = DSL.field(DSL.name("outbox_messages", "created_at"), OffsetDateTime::class.java)
        val PUBLISHED_AT = DSL.field(DSL.name("outbox_messages", "published_at"), OffsetDateTime::class.java)

        // Unqualified for INSERT / UPDATE
        private val COL_ID = DSL.field("id", UUID::class.java)
        private val COL_AGGREGATE_TYPE = DSL.field("aggregate_type", String::class.java)
        private val COL_AGGREGATE_ID = DSL.field("aggregate_id", UUID::class.java)
        private val COL_PUBLISHED_AT = DSL.field("published_at", OffsetDateTime::class.java)
    }

    override fun insert(aggregateType: String, aggregateId: UUID, payload: String) {
        dsl.insertInto(TABLE)
            .set(COL_ID, UUID.randomUUID())
            .set(COL_AGGREGATE_TYPE, aggregateType)
            .set(COL_AGGREGATE_ID, aggregateId)
            .set(DSL.field("payload"), DSL.value(payload).cast(SQLDataType.JSONB))
            .execute()
    }

    override fun findUnpublished(limit: Int): List<OutboxMessageEntity> = dsl.select(ID, AGGREGATE_TYPE, AGGREGATE_ID, PAYLOAD, CREATED_AT)
        .from(TABLE)
        .where(PUBLISHED_AT.isNull)
        .orderBy(CREATED_AT.asc())
        .limit(limit)
        .fetch()
        .map { r ->
            OutboxMessageEntity(
                id = r.get(ID)!!,
                aggregateType = r.get(AGGREGATE_TYPE)!!,
                aggregateId = r.get(AGGREGATE_ID)!!,
                payload = r.get(PAYLOAD)!!,
                createdAt = r.get(CREATED_AT)!!,
                publishedAt = null,
            )
        }

    override fun markPublished(id: UUID) {
        dsl.update(TABLE)
            .set(COL_PUBLISHED_AT, OffsetDateTime.now())
            .where(ID.eq(id))
            .execute()
    }
}
