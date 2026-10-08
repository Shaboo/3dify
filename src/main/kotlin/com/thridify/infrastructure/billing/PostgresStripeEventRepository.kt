package com.thridify.infrastructure.billing
import com.thridify.domain.billing.StripeEventRepository
import com.thridify.domain.billing.VerifiedBillingEvent
import com.thridify.infrastructure.persistence.DirectWorkspaceLookup
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID
@Repository
class PostgresStripeEventRepository(private val dsl: DSLContext, private val workspaces: DirectWorkspaceLookup) : StripeEventRepository {
    override fun latest(userId: UUID): OffsetDateTime? = dsl.fetchOne("SELECT max(event_created_at) AS latest FROM stripe_event_receipts WHERE billing_scope_id = ?", workspaces.billingScopeId(userId))?.get("latest", OffsetDateTime::class.java)
    override fun record(userId: UUID, event: VerifiedBillingEvent) = dsl.execute("INSERT INTO stripe_event_receipts(event_id, billing_scope_id, event_created_at) VALUES (?, ?, ?::timestamptz) ON CONFLICT DO NOTHING", event.id, workspaces.billingScopeId(userId), event.createdAt) == 1
}
