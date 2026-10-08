package com.thridify.infrastructure.persistence

import com.thridify.domain.generation.DirectGenerationAccount
import com.thridify.domain.generation.DirectGenerationAllowance
import com.thridify.domain.generation.DirectGenerationRepository
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class PostgresDirectGenerationRepository(private val dsl: DSLContext) : DirectGenerationRepository {
    override fun lockAccount(apiKeyId: UUID): DirectGenerationAccount? {
        val scope = dsl.fetchOne("SELECT b.id FROM billing_scopes b JOIN api_keys k ON k.billing_scope_id = b.id WHERE k.id = ? AND k.is_active AND k.revoked_at IS NULL FOR UPDATE OF b", apiKeyId)?.get("id", UUID::class.java) ?: return null
        val row = dsl.fetchOne("SELECT s.*, p.monthly_quota FROM subscriptions s JOIN plans p ON p.id = s.plan_id WHERE s.billing_scope_id = ? AND s.status NOT IN ('canceled','expired','incomplete_expired')", scope) ?: return null
        return DirectGenerationAccount(scope, row.get("provider", String::class.java)!!, row.get("status", String::class.java)!!, row.get("monthly_quota", Int::class.java)!!, row.get("current_period_start", OffsetDateTime::class.java), row.get("current_period_end", OffsetDateTime::class.java))
    }
    override fun consume(scopeId: UUID, allowance: DirectGenerationAllowance): Boolean {
        dsl.execute("INSERT INTO usage_periods(billing_scope_id,period_start,period_end,generation_limit) VALUES (?,?,?,?) ON CONFLICT(billing_scope_id,period_start) DO UPDATE SET period_end = EXCLUDED.period_end, generation_limit = GREATEST(EXCLUDED.generation_limit,usage_periods.generations_consumed + usage_periods.generations_reserved)", scopeId, Timestamp.from(allowance.start.toInstant()), Timestamp.from(allowance.end.toInstant()), allowance.limit)
        return dsl.execute("UPDATE usage_periods SET generations_consumed = generations_consumed + 1 WHERE billing_scope_id = ? AND period_start = ? AND generations_consumed + generations_reserved < generation_limit", scopeId, Timestamp.from(allowance.start.toInstant())) == 1
    }
}
