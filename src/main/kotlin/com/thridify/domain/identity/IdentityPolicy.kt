package com.thridify.domain.identity

import com.thridify.domain.identity.UserEntity
import com.thridify.shared.exception.ConflictException
import com.thridify.shared.exception.UnauthorizedException
import org.springframework.stereotype.Component

@Component
class IdentityPolicy {
    fun credentials(email: String, password: String) {
        if (email.length !in 3..255 || !Regex("^[^\\s@]+@[^\\s@]+[.][^\\s@]+$").matches(email)) throw com.thridify.shared.exception.BadRequestException("Enter a valid email address")
        if (password.isBlank() || password.toByteArray(Charsets.UTF_8).size > 72) throw com.thridify.shared.exception.BadRequestException("Password must be non-empty and at most 72 UTF-8 bytes")
    }
    fun registration(email: String, password: String, name: String?) {
        credentials(email, password)
        if (name != null && name.length > 255) throw com.thridify.shared.exception.BadRequestException("Name must be at most 255 characters")
    }

    fun ensureEmailAvailable(exists: Boolean) {
        if (exists) throw ConflictException("Email already registered")
    }
    fun requireUser(user: UserEntity?): UserEntity = user ?: throw UnauthorizedException("Invalid credentials")
    fun ensurePasswordMatches(matches: Boolean) {
        if (!matches) throw UnauthorizedException("Invalid credentials")
    }
}
