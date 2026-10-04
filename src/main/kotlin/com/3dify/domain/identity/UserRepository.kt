package com.`3dify`.domain.identity

import com.`3dify`.domain.identity.UserEntity
import java.util.UUID

interface UserRepository {
    fun existsByEmail(email: String): Boolean
    fun insert(id: UUID, email: String, passwordHash: String, name: String?): Unit
    fun findByEmail(email: String): UserEntity?
    fun findById(id: UUID): UserEntity?
}
