package com.`3dify`.application.service.identity

import java.util.UUID

data class AuthResult(
    val token: String,
    val userId: UUID,
    val email: String,
    val isAdmin: Boolean = false,
)
