package com.thridify.domain.identity

import com.thridify.domain.identity.UserEntity
import java.util.UUID

interface UserRepository {
    fun existsByEmail(email: String): Boolean
    fun insert(id: UUID, email: String, passwordHash: String, name: String?): Unit
    fun findByEmail(email: String): UserEntity?
    fun findById(id: UUID): UserEntity?
}
