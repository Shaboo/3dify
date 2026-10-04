package com.`3dify`.domain.identity

import java.util.UUID

data class UserEntity(
    val id: UUID,
    val email: String,
    val passwordHash: String,
    val name: String?,
    val isAdmin: Boolean,
)
