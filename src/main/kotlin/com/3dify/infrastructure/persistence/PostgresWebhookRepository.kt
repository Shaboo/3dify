package com.`3dify`.infrastructure.persistence

import com.`3dify`.domain.webhook.WebhookEntity
import com.`3dify`.domain.webhook.WebhookRepository
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository("webhookRepository")
class PostgresWebhookRepository(private val dsl: DSLContext) : WebhookRepository {

    private companion object {
        val TABLE = DSL.table("webhooks")
        val ID = DSL.field("id", UUID::class.java)
        val USER_ID = DSL.field("user_id", UUID::class.java)
        val URL = DSL.field("url", String::class.java)
        val CREATED_AT = DSL.field("created_at", OffsetDateTime::class.java)
        val UPDATED_AT = DSL.field("updated_at", OffsetDateTime::class.java)
    }

    override fun findByUserId(userId: UUID): WebhookEntity? = dsl.select(ID, USER_ID, URL, CREATED_AT, UPDATED_AT)
        .from(TABLE)
        .where(USER_ID.eq(userId))
        .fetchOne()
        ?.let { r ->
            WebhookEntity(
                id = r.get(ID)!!,
                userId = r.get(USER_ID)!!,
                url = r.get(URL)!!,
                createdAt = r.get(CREATED_AT)!!,
                updatedAt = r.get(UPDATED_AT),
            )
        }

    override fun upsert(userId: UUID, url: String) {
        if (dsl.fetchExists(dsl.selectFrom(TABLE).where(USER_ID.eq(userId)))) {
            dsl.update(TABLE)
                .set(URL, url)
                .set(UPDATED_AT, OffsetDateTime.now())
                .where(USER_ID.eq(userId))
                .execute()
        } else {
            dsl.insertInto(TABLE)
                .set(ID, UUID.randomUUID())
                .set(USER_ID, userId)
                .set(URL, url)
                .execute()
        }
    }

    /** @return 1 if deleted, 0 if not found. */
    override fun deleteByUserId(userId: UUID): Int = dsl.deleteFrom(TABLE).where(USER_ID.eq(userId)).execute()
}
