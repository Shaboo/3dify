package com.thridify.infrastructure.persistence

import org.jooq.DSLContext
import org.springframework.stereotype.Component
import java.util.UUID

/** Bridge for existing direct-user endpoints until they accept explicit workspace context. */
@Component
class DirectWorkspaceLookup(private val dsl: DSLContext) {
    fun workspaceId(userId: UUID): UUID = dsl.fetchOne(
        "SELECT workspace_id FROM workspace_memberships WHERE user_id = ? AND is_default",
        userId,
    )?.get("workspace_id", UUID::class.java) ?: error("No default workspace for user $userId")

    fun billingScopeId(userId: UUID): UUID = dsl.fetchOne(
        "SELECT id FROM billing_scopes WHERE workspace_id = ? AND connection_id IS NULL",
        workspaceId(userId),
    )?.get("id", UUID::class.java) ?: error("No direct billing scope for user $userId")
}
