package com.thridify.infrastructure.billing
import com.thridify.domain.billing.BillingCommandRecord
import com.thridify.domain.billing.BillingCommandRepository
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
@Repository
class PostgresBillingCommandRepository(private val dsl: DSLContext) : BillingCommandRepository {
    override fun reserve(key: String, fingerprint: String): BillingCommandRecord {
        dsl.execute("INSERT INTO billing_commands(command_key, fingerprint) VALUES (?, ?) ON CONFLICT DO NOTHING", key, fingerprint)
        return toRecord(dsl.fetchOne("SELECT * FROM billing_commands WHERE command_key = ?", key)!!)
    }
    override fun lock(key: String) = toRecord(dsl.fetchOne("SELECT * FROM billing_commands WHERE command_key = ? FOR UPDATE", key)!!)
    override fun recordProviderResult(key: String, value: String) {
        dsl.execute("UPDATE billing_commands SET provider_result = COALESCE(provider_result, ?) WHERE command_key = ?", value, key)
    }
    override fun complete(key: String, result: String) {
        dsl.execute("UPDATE billing_commands SET result = COALESCE(result, ?) WHERE command_key = ?", result, key)
    }
    private fun toRecord(row: Record) = BillingCommandRecord(row.get("command_key", String::class.java)!!, row.get("fingerprint", String::class.java)!!, row.get("created_at", OffsetDateTime::class.java)!!, row.get("provider_result", String::class.java), row.get("result", String::class.java))
}
