package com.thridify.infrastructure.persistence

import com.thridify.domain.identity.UserEntity
import com.thridify.domain.identity.UserRepository
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository("userRepository")
class PostgresUserRepository(private val dsl: DSLContext) : UserRepository {

    // All fields are unqualified — `users` is never joined, so no collision risk.
    private companion object {
        val TABLE = DSL.table("users")
        val ID = DSL.field("id", UUID::class.java)
        val EMAIL = DSL.field("email", String::class.java)
        val NAME = DSL.field("name", String::class.java)
        val HASH = DSL.field("password_hash", String::class.java)
        val IS_ADMIN = DSL.field("is_admin", Boolean::class.java)
    }

    override fun existsByEmail(email: String): Boolean = dsl.fetchExists(dsl.selectOne().from(TABLE).where(EMAIL.eq(email)))

    override fun insert(id: UUID, email: String, passwordHash: String, name: String?) {
        val workspaceId = UUID.randomUUID()
        dsl.execute(
            """
            WITH new_user AS (
                INSERT INTO users (id, email, password_hash, name) VALUES (?, ?, ?, ?) RETURNING id
            ), new_workspace AS (
                INSERT INTO workspaces (id, name) SELECT ?, ? FROM new_user RETURNING id
            ), new_membership AS (
                INSERT INTO workspace_memberships (workspace_id, user_id, role, is_default)
                SELECT new_workspace.id, new_user.id, 'owner', true FROM new_workspace CROSS JOIN new_user
            )
            INSERT INTO billing_scopes (workspace_id) SELECT id FROM new_workspace
            """.trimIndent(),
            id,
            email,
            passwordHash,
            name,
            workspaceId,
            name ?: email,
        )
    }

    override fun findByEmail(email: String): UserEntity? = dsl.select(ID, EMAIL, HASH, IS_ADMIN)
        .from(TABLE)
        .where(EMAIL.eq(email).and(HASH.isNotNull))
        .fetchOne()
        ?.let { r ->
            UserEntity(
                id = r.get(ID)!!,
                email = r.get(EMAIL)!!,
                passwordHash = r.get(HASH)!!,
                name = null, // not fetched in auth path
                isAdmin = r.get(IS_ADMIN) ?: false,
            )
        }

    override fun findById(id: UUID): UserEntity? = dsl.select(ID, EMAIL, NAME, IS_ADMIN)
        .from(TABLE)
        .where(ID.eq(id))
        .fetchOne()
        ?.let { r ->
            UserEntity(
                id = r.get(ID)!!,
                email = r.get(EMAIL)!!,
                passwordHash = "", // not fetched in profile path
                name = r.get(NAME),
                isAdmin = r.get(IS_ADMIN) ?: false,
            )
        }
}
