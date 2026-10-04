package com.`3dify`.domain.identity

import com.`3dify`.domain.identity.UserEntity
import com.`3dify`.shared.exception.ConflictException
import com.`3dify`.shared.exception.UnauthorizedException
import org.springframework.stereotype.Component

@Component
class IdentityPolicy {
    fun ensureEmailAvailable(exists: Boolean) {
        if (exists) throw ConflictException("Email already registered")
    }
    fun requireUser(user: UserEntity?): UserEntity = user ?: throw UnauthorizedException("Invalid credentials")
    fun ensurePasswordMatches(matches: Boolean) {
        if (!matches) throw UnauthorizedException("Invalid credentials")
    }
}
