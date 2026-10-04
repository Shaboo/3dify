package com.`3dify`.infrastructure.persistence

import com.`3dify`.domain.apikey.ApiKeyAuthEntity
import com.`3dify`.domain.apikey.ApiKeyRepository
import com.`3dify`.domain.apikey.ApiKeyWithPlanEntity
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository("apiKeyRepository")
class PostgresApiKeyRepository(private val dsl: DSLContext) : ApiKeyRepository {

    private companion object {
        val KEYS = DSL.table("api_keys")
        val PLANS = DSL.table("plans")

        // api_keys columns (qualified for JOINs)
        val AK_ID = DSL.field(DSL.name("api_keys", "id"), UUID::class.java)
        val AK_USER_ID = DSL.field(DSL.name("api_keys", "user_id"), UUID::class.java)
        val AK_PLAN_ID = DSL.field(DSL.name("api_keys", "plan_id"), UUID::class.java)
        val AK_KEY_HASH = DSL.field(DSL.name("api_keys", "key_hash"), String::class.java)
        val AK_KEY_PREFIX = DSL.field(DSL.name("api_keys", "key_prefix"), String::class.java)
        val AK_LABEL = DSL.field(DSL.name("api_keys", "label"), String::class.java)
        val AK_IS_ACTIVE = DSL.field(DSL.name("api_keys", "is_active"), Boolean::class.java)
        val AK_CREATED_AT = DSL.field(DSL.name("api_keys", "created_at"), OffsetDateTime::class.java)
        val AK_REVOKED_AT = DSL.field(DSL.name("api_keys", "revoked_at"), OffsetDateTime::class.java)

        // plans columns (qualified for JOINs)
        val P_ID = DSL.field(DSL.name("plans", "id"), UUID::class.java)
        val P_NAME = DSL.field(DSL.name("plans", "name"), String::class.java)
        val P_RATE_LIMIT = DSL.field(DSL.name("plans", "rate_limit_rpm"), Int::class.java)

        // Unqualified for INSERT / UPDATE SET
        private val COL_ID = DSL.field("id", UUID::class.java)
        private val COL_USER_ID = DSL.field("user_id", UUID::class.java)
        private val COL_PLAN_ID = DSL.field("plan_id", UUID::class.java)
        private val COL_KEY_HASH = DSL.field("key_hash", String::class.java)
        private val COL_KEY_PREFIX = DSL.field("key_prefix", String::class.java)
        private val COL_LABEL = DSL.field("label", String::class.java)
        private val COL_IS_ACTIVE = DSL.field("is_active", Boolean::class.java)
        private val COL_PLAN_ID_UQ = DSL.field("plan_id", UUID::class.java)
        private val COL_REVOKED_AT = DSL.field("revoked_at", OffsetDateTime::class.java)
    }

    override fun insert(id: UUID, userId: UUID, planId: UUID, keyHash: String, keyPrefix: String, label: String?) {
        dsl.insertInto(KEYS)
            .set(COL_ID, id)
            .set(COL_USER_ID, userId)
            .set(COL_PLAN_ID, planId)
            .set(COL_KEY_HASH, keyHash)
            .set(COL_KEY_PREFIX, keyPrefix)
            .set(COL_LABEL, label)
            .execute()
    }

    override fun findAllByUserId(userId: UUID): List<ApiKeyWithPlanEntity> = dsl.select(AK_ID, AK_KEY_PREFIX, AK_LABEL, P_NAME, AK_IS_ACTIVE, AK_CREATED_AT, AK_REVOKED_AT)
        .from(KEYS)
        .join(PLANS).on(AK_PLAN_ID.eq(P_ID))
        .where(AK_USER_ID.eq(userId))
        .orderBy(AK_CREATED_AT.desc())
        .fetch()
        .map { r ->
            ApiKeyWithPlanEntity(
                id = r.get(AK_ID)!!,
                keyPrefix = r.get(AK_KEY_PREFIX)!!,
                label = r.get(AK_LABEL),
                planName = r.get(P_NAME)!!,
                isActive = r.get(AK_IS_ACTIVE)!!,
                createdAt = r.get(AK_CREATED_AT)!!,
                revokedAt = r.get(AK_REVOKED_AT),
            )
        }

    /** Returns 1 if revoked, 0 if key not found for that user. */
    override fun revoke(userId: UUID, keyId: UUID): Int = dsl.update(KEYS)
        .set(COL_IS_ACTIVE, false)
        .set(COL_REVOKED_AT, OffsetDateTime.now())
        .where(AK_ID.eq(keyId).and(AK_USER_ID.eq(userId)))
        .execute()

    override fun findByKeyHash(hash: String): ApiKeyAuthEntity? = dsl.select(AK_ID, AK_USER_ID, AK_IS_ACTIVE, P_RATE_LIMIT)
        .from(KEYS)
        .join(PLANS).on(AK_PLAN_ID.eq(P_ID))
        .where(AK_KEY_HASH.eq(hash))
        .fetchOne()
        ?.let { r ->
            ApiKeyAuthEntity(
                id = r.get(AK_ID)!!,
                userId = r.get(AK_USER_ID)!!,
                isActive = r.get(AK_IS_ACTIVE)!!,
                rateLimitRpm = r.get(P_RATE_LIMIT)!!,
            )
        }

    override fun updatePlanForUser(userId: UUID, planId: UUID) {
        dsl.update(KEYS)
            .set(COL_PLAN_ID_UQ, planId)
            .where(AK_USER_ID.eq(userId))
            .and(AK_IS_ACTIVE.isTrue)
            .execute()
    }

    override fun setActiveByUserId(userId: UUID, active: Boolean) {
        val q = dsl.update(KEYS).set(COL_IS_ACTIVE, active)
        if (!active) q.set(COL_REVOKED_AT, OffsetDateTime.now())
        q.where(AK_USER_ID.eq(userId)).execute()
    }
}
