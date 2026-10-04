package com.thridify.domain.identity

import com.thridify.domain.identity.UserEntity
import com.thridify.shared.exception.ConflictException
import com.thridify.shared.exception.UnauthorizedException
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
