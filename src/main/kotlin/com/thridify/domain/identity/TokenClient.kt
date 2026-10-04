package com.thridify.domain.identity

import java.util.UUID

interface TokenClient {
    fun generateToken(userId: UUID, email: String): String
    fun subject(token: String): String?
}
