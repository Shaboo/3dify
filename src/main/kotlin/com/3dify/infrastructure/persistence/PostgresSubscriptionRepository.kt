package com.`3dify`.infrastructure.persistence

import com.`3dify`.domain.subscription.SubscriptionEntity
import com.`3dify`.domain.subscription.SubscriptionRepository
import com.`3dify`.domain.subscription.SubscriptionWithPlanEntity
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository("subscriptionRepository")
class PostgresSubscriptionRepository(private val dsl: DSLContext) : SubscriptionRepository {

    private companion object {
        val SUBS = DSL.table("subscriptions")
        val PLANS = DSL.table("plans")

        // subscriptions columns (qualified for JOINs)
        val S_ID = DSL.field(DSL.name("subscriptions", "id"), UUID::class.java)
        val S_USER_ID = DSL.field(DSL.name("subscriptions", "user_id"), UUID::class.java)
        val S_PLAN_ID = DSL.field(DSL.name("subscriptions", "plan_id"), UUID::class.java)
        val S_STRIPE_SUB_ID = DSL.field(DSL.name("subscriptions", "stripe_subscription_id"), String::class.java)
        val S_STRIPE_CUST_ID = DSL.field(DSL.name("subscriptions", "stripe_customer_id"), String::class.java)
        val S_STATUS = DSL.field(DSL.name("subscriptions", "status"), String::class.java)
        val S_PERIOD_END = DSL.field(DSL.name("subscriptions", "current_period_end"), OffsetDateTime::class.java)
        val S_CREATED_AT = DSL.field(DSL.name("subscriptions", "created_at"), OffsetDateTime::class.java)
        val S_UPDATED_AT = DSL.field(DSL.name("subscriptions", "updated_at"), OffsetDateTime::class.java)

        // plans columns (qualified for JOINs)
        val P_ID = DSL.field(DSL.name("plans", "id"), UUID::class.java)
        val P_NAME = DSL.field(DSL.name("plans", "name"), String::class.java)
        val P_DISPLAY_NAME = DSL.field(DSL.name("plans", "display_name"), String::class.java)
        val P_RATE_LIMIT = DSL.field(DSL.name("plans", "rate_limit_rpm"), Int::class.java)
        val P_MONTHLY_QUOTA = DSL.field(DSL.name("plans", "monthly_quota"), Int::class.java)
        val P_PRICE_CENTS = DSL.field(DSL.name("plans", "price_cents"), Int::class.java)

        // Unqualified for INSERT / UPDATE
        private val COL_USER_ID = DSL.field("user_id", UUID::class.java)
        private val COL_PLAN_ID = DSL.field("plan_id", UUID::class.java)
        private val COL_STRIPE_SUB_ID = DSL.field("stripe_subscription_id", String::class.java)
        private val COL_STRIPE_CUST = DSL.field("stripe_customer_id", String::class.java)
        private val COL_STATUS = DSL.field("status", String::class.java)
        private val COL_PERIOD_END = DSL.field("current_period_end", OffsetDateTime::class.java)
        private val COL_UPDATED_AT = DSL.field("updated_at", OffsetDateTime::class.java)

        private val WITH_PLAN_COLS = arrayOf(
            S_ID, S_USER_ID, S_PLAN_ID, S_STRIPE_SUB_ID, S_STRIPE_CUST_ID,
            S_STATUS, S_PERIOD_END, S_CREATED_AT, S_UPDATED_AT,
            P_NAME, P_DISPLAY_NAME, P_RATE_LIMIT, P_MONTHLY_QUOTA, P_PRICE_CENTS,
        )

        private val SUB_COLS = arrayOf(
            S_ID, S_USER_ID, S_PLAN_ID, S_STRIPE_SUB_ID, S_STRIPE_CUST_ID,
            S_STATUS, S_PERIOD_END, S_CREATED_AT, S_UPDATED_AT,
        )
    }

    override fun findActiveByUserId(userId: UUID): SubscriptionWithPlanEntity? = dsl.select(*WITH_PLAN_COLS)
        .from(SUBS)
        .join(PLANS).on(S_PLAN_ID.eq(P_ID))
        .where(S_USER_ID.eq(userId))
        .and(S_STATUS.notIn("canceled"))
        .fetchOne()
        ?.let(::toWithPlan)

    override fun findByStripeSubId(stripeSubId: String): SubscriptionEntity? = dsl.select(*SUB_COLS)
        .from(SUBS)
        .where(S_STRIPE_SUB_ID.eq(stripeSubId))
        .fetchOne()
        ?.let(::toEntity)

    override fun findByStripeCustomerId(stripeCustomerId: String): SubscriptionEntity? = dsl.select(*SUB_COLS)
        .from(SUBS)
        .where(S_STRIPE_CUST_ID.eq(stripeCustomerId))
        .and(S_STATUS.notIn("canceled"))
        .fetchOne()
        ?.let(::toEntity)

    override fun insert(
        userId: UUID,
        planId: UUID,
        stripeSubId: String?,
        stripeCustomerId: String?,
        status: String,
        currentPeriodEnd: OffsetDateTime?,
    ) {
        dsl.insertInto(SUBS)
            .set(COL_USER_ID, userId)
            .set(COL_PLAN_ID, planId)
            .set(COL_STRIPE_SUB_ID, stripeSubId)
            .set(COL_STRIPE_CUST, stripeCustomerId)
            .set(COL_STATUS, status)
            .set(COL_PERIOD_END, currentPeriodEnd)
            .execute()
    }

    override fun upsertByUserId(
        userId: UUID,
        planId: UUID,
        stripeSubId: String?,
        stripeCustomerId: String?,
        status: String,
        currentPeriodEnd: OffsetDateTime?,
    ) {
        val exists = dsl.fetchExists(
            dsl.selectOne().from(SUBS)
                .where(S_USER_ID.eq(userId))
                .and(S_STATUS.notIn("canceled")),
        )
        if (exists) {
            dsl.update(SUBS)
                .set(COL_PLAN_ID, planId)
                .set(COL_STRIPE_SUB_ID, stripeSubId)
                .set(COL_STRIPE_CUST, stripeCustomerId)
                .set(COL_STATUS, status)
                .set(COL_PERIOD_END, currentPeriodEnd)
                .set(COL_UPDATED_AT, OffsetDateTime.now())
                .where(S_USER_ID.eq(userId))
                .and(S_STATUS.notIn("canceled"))
                .execute()
        } else {
            insert(userId, planId, stripeSubId, stripeCustomerId, status, currentPeriodEnd)
        }
    }

    override fun updateStatusByStripeSubId(stripeSubId: String, status: String, currentPeriodEnd: OffsetDateTime?) {
        dsl.update(SUBS)
            .set(COL_STATUS, status)
            .set(COL_PERIOD_END, currentPeriodEnd)
            .set(COL_UPDATED_AT, OffsetDateTime.now())
            .where(S_STRIPE_SUB_ID.eq(stripeSubId))
            .execute()
    }

    override fun updateStatusByStripeCustomerId(stripeCustomerId: String, status: String) {
        dsl.update(SUBS)
            .set(COL_STATUS, status)
            .set(COL_UPDATED_AT, OffsetDateTime.now())
            .where(S_STRIPE_CUST_ID.eq(stripeCustomerId))
            .execute()
    }

    private fun toEntity(r: org.jooq.Record) = SubscriptionEntity(
        id = r.get(S_ID)!!,
        userId = r.get(S_USER_ID)!!,
        planId = r.get(S_PLAN_ID)!!,
        stripeSubscriptionId = r.get(S_STRIPE_SUB_ID),
        stripeCustomerId = r.get(S_STRIPE_CUST_ID),
        status = r.get(S_STATUS)!!,
        currentPeriodEnd = r.get(S_PERIOD_END),
        createdAt = r.get(S_CREATED_AT)!!,
        updatedAt = r.get(S_UPDATED_AT),
    )

    private fun toWithPlan(r: org.jooq.Record) = SubscriptionWithPlanEntity(
        id = r.get(S_ID)!!,
        userId = r.get(S_USER_ID)!!,
        planId = r.get(S_PLAN_ID)!!,
        stripeSubscriptionId = r.get(S_STRIPE_SUB_ID),
        stripeCustomerId = r.get(S_STRIPE_CUST_ID),
        status = r.get(S_STATUS)!!,
        currentPeriodEnd = r.get(S_PERIOD_END),
        planName = r.get(P_NAME)!!,
        planDisplayName = r.get(P_DISPLAY_NAME) ?: "",
        planRateLimitRpm = r.get(P_RATE_LIMIT) ?: 0,
        planMonthlyQuota = r.get(P_MONTHLY_QUOTA) ?: 0,
        planPriceCents = r.get(P_PRICE_CENTS) ?: 0,
    )
}
