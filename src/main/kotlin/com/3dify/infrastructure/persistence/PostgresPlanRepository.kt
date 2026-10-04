package com.`3dify`.infrastructure.persistence

import com.`3dify`.domain.plan.PlanEntity
import com.`3dify`.domain.plan.PlanRepository
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository("planRepository")
class PostgresPlanRepository(private val dsl: DSLContext) : PlanRepository {

    private companion object {
        val TABLE = DSL.table("plans")
        val ID = DSL.field("id", UUID::class.java)
        val NAME = DSL.field("name", String::class.java)
        val DISPLAY_NAME = DSL.field("display_name", String::class.java)
        val DESCRIPTION = DSL.field("description", String::class.java)
        val RATE_LIMIT = DSL.field("rate_limit_rpm", Int::class.java)
        val MONTHLY_QUOTA = DSL.field("monthly_quota", Int::class.java)
        val PRICE_CENTS = DSL.field("price_cents", Int::class.java)
        val CURRENCY = DSL.field("currency", String::class.java)
        val STRIPE_PRICE = DSL.field("stripe_price_id", String::class.java)
        val IS_ACTIVE = DSL.field("is_active", Boolean::class.java)
        val SORT_ORDER = DSL.field("sort_order", Int::class.java)

        private val ALL_COLS = arrayOf(
            ID, NAME, DISPLAY_NAME, DESCRIPTION, RATE_LIMIT,
            MONTHLY_QUOTA, PRICE_CENTS, CURRENCY, STRIPE_PRICE, IS_ACTIVE, SORT_ORDER,
        )
    }

    override fun findAll(): List<PlanEntity> = dsl.select(*ALL_COLS).from(TABLE).orderBy(SORT_ORDER.asc()).fetch().map(::toEntity)

    override fun findAllActive(): List<PlanEntity> = dsl.select(*ALL_COLS).from(TABLE).where(IS_ACTIVE.isTrue).orderBy(SORT_ORDER.asc()).fetch().map(::toEntity)

    override fun findById(id: UUID): PlanEntity? = dsl.select(*ALL_COLS).from(TABLE).where(ID.eq(id)).fetchOne()?.let(::toEntity)

    override fun findByName(name: String): PlanEntity? = dsl.select(*ALL_COLS).from(TABLE).where(NAME.eq(name)).fetchOne()?.let(::toEntity)

    override fun insert(
        name: String,
        displayName: String,
        description: String?,
        rateLimitRpm: Int,
        monthlyQuota: Int,
        priceCents: Int,
        currency: String,
        stripePriceId: String?,
        sortOrder: Int,
    ): UUID {
        val id = UUID.randomUUID()
        dsl.insertInto(TABLE)
            .set(ID, id)
            .set(NAME, name)
            .set(DISPLAY_NAME, displayName)
            .set(DESCRIPTION, description)
            .set(RATE_LIMIT, rateLimitRpm)
            .set(MONTHLY_QUOTA, monthlyQuota)
            .set(PRICE_CENTS, priceCents)
            .set(CURRENCY, currency)
            .set(STRIPE_PRICE, stripePriceId)
            .set(SORT_ORDER, sortOrder)
            .execute()
        return id
    }

    override fun update(
        id: UUID,
        displayName: String?,
        description: String?,
        priceCents: Int?,
        rateLimitRpm: Int?,
        monthlyQuota: Int?,
        stripePriceId: String?,
        sortOrder: Int?,
    ) {
        @Suppress("UNCHECKED_CAST")
        val updates = buildMap<org.jooq.Field<Any?>, Any?> {
            displayName?.let { put(DISPLAY_NAME as org.jooq.Field<Any?>, it) }
            description?.let { put(DESCRIPTION as org.jooq.Field<Any?>, it) }
            priceCents?.let { put(PRICE_CENTS as org.jooq.Field<Any?>, it) }
            rateLimitRpm?.let { put(RATE_LIMIT as org.jooq.Field<Any?>, it) }
            monthlyQuota?.let { put(MONTHLY_QUOTA as org.jooq.Field<Any?>, it) }
            stripePriceId?.let { put(STRIPE_PRICE as org.jooq.Field<Any?>, it) }
            sortOrder?.let { put(SORT_ORDER as org.jooq.Field<Any?>, it) }
        }
        if (updates.isEmpty()) return
        dsl.update(TABLE).set(updates).where(ID.eq(id)).execute()
    }

    override fun deactivate(id: UUID) {
        dsl.update(TABLE).set(IS_ACTIVE, false).where(ID.eq(id)).execute()
    }

    private fun toEntity(r: org.jooq.Record) = PlanEntity(
        id = r.get(ID)!!,
        name = r.get(NAME)!!,
        displayName = r.get(DISPLAY_NAME) ?: "",
        description = r.get(DESCRIPTION),
        rateLimitRpm = r.get(RATE_LIMIT) ?: 0,
        monthlyQuota = r.get(MONTHLY_QUOTA) ?: 0,
        priceCents = r.get(PRICE_CENTS) ?: 0,
        currency = r.get(CURRENCY) ?: "usd",
        stripePriceId = r.get(STRIPE_PRICE),
        isActive = r.get(IS_ACTIVE) ?: true,
        sortOrder = r.get(SORT_ORDER) ?: 0,
    )
}
