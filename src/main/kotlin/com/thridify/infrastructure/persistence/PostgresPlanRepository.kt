package com.thridify.infrastructure.persistence

import com.thridify.domain.plan.PlanEntity
import com.thridify.domain.plan.PlanRepository
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
        val STRIPE_PRICE = DSL.field("(SELECT external_offer_id FROM plan_offers WHERE plan_id = plans.id AND provider = 'stripe' AND billing_interval = 'monthly')", String::class.java).`as`("stripe_price_id")
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
        dsl.execute(
            """
            WITH new_plan AS (
                INSERT INTO plans (id, name, display_name, description, rate_limit_rpm, monthly_quota,
                                   price_cents, currency, sort_order)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
            )
            INSERT INTO plan_offers (plan_id, provider, external_offer_id)
            SELECT id, 'stripe', ?::varchar FROM new_plan WHERE ?::varchar IS NOT NULL
            """.trimIndent(),
            id, name, displayName, description, rateLimitRpm, monthlyQuota, priceCents, currency,
            sortOrder, stripePriceId, stripePriceId,
        )
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
            sortOrder?.let { put(SORT_ORDER as org.jooq.Field<Any?>, it) }
        }
        stripePriceId?.let { saveStripeOffer(id, it) }
        if (updates.isEmpty()) return
        dsl.update(TABLE).set(updates).where(ID.eq(id)).execute()
    }

    override fun deactivate(id: UUID) {
        dsl.update(TABLE).set(IS_ACTIVE, false).where(ID.eq(id)).execute()
    }

    private fun saveStripeOffer(planId: UUID, priceId: String) {
        dsl.execute(
            """
            INSERT INTO plan_offers (plan_id, provider, external_offer_id)
            VALUES (?, 'stripe', ?)
            ON CONFLICT (plan_id, provider, billing_interval)
            DO UPDATE SET external_offer_id = EXCLUDED.external_offer_id
            """.trimIndent(),
            planId,
            priceId,
        )
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
