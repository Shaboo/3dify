package com.omni3d.infrastructure.persistence

import com.omni3d.domain.UserEntity
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class UserRepository(private val dsl: DSLContext) {

    // All fields are unqualified — `users` is never joined, so no collision risk.
    private companion object {
        val TABLE   = DSL.table("users")
        val ID      = DSL.field("id",            UUID::class.java)
        val EMAIL   = DSL.field("email",          String::class.java)
        val NAME    = DSL.field("name",           String::class.java)
        val HASH    = DSL.field("password_hash",  String::class.java)
        val IS_ADMIN= DSL.field("is_admin",       Boolean::class.java)
    }

    fun existsByEmail(email: String): Boolean =
        dsl.fetchExists(dsl.selectOne().from(TABLE).where(EMAIL.eq(email)))

    fun insert(id: UUID, email: String, passwordHash: String, name: String?) {
        dsl.insertInto(TABLE)
            .set(ID,     id)
            .set(EMAIL,  email)
            .set(HASH,   passwordHash)
            .set(NAME,   name)
            .execute()
    }

    fun findByEmail(email: String): UserEntity? =
        dsl.select(ID, EMAIL, HASH, IS_ADMIN)
            .from(TABLE)
            .where(EMAIL.eq(email))
            .fetchOne()
            ?.let { r -> UserEntity(
                id           = r.get(ID)!!,
                email        = r.get(EMAIL)!!,
                passwordHash = r.get(HASH)!!,
                name         = null,          // not fetched in auth path
                isAdmin      = r.get(IS_ADMIN) ?: false
            )}

    fun findById(id: UUID): UserEntity? =
        dsl.select(ID, EMAIL, NAME, IS_ADMIN)
            .from(TABLE)
            .where(ID.eq(id))
            .fetchOne()
            ?.let { r -> UserEntity(
                id           = r.get(ID)!!,
                email        = r.get(EMAIL)!!,
                passwordHash = "",            // not fetched in profile path
                name         = r.get(NAME),
                isAdmin      = r.get(IS_ADMIN) ?: false
            )}
}
