package com.thridify.infrastructure.persistence

import com.thridify.domain.generation.PendingInputRepository
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.OffsetDateTime

@Repository
class PostgresPendingInputRepository(private val dsl: DSLContext) : PendingInputRepository {
    override fun record(keys: List<String>) {
        keys.forEach { dsl.execute("INSERT INTO pending_input_uploads(object_key) VALUES (?) ON CONFLICT DO NOTHING", it) }
    }
    override fun release(keys: List<String>) {
        keys.forEach { dsl.execute("DELETE FROM pending_input_uploads WHERE object_key = ?", it) }
    }
    override fun expired(before: OffsetDateTime): List<String> = dsl.fetch("SELECT object_key FROM pending_input_uploads p WHERE created_at < ? AND NOT EXISTS(SELECT 1 FROM jobs j WHERE p.object_key = ANY(j.input_images)) ORDER BY created_at LIMIT 50", Timestamp.from(before.toInstant())).map { it.get("object_key", String::class.java)!! }
}
