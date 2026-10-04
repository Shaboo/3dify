package com.`3dify`.infrastructure.security

import com.`3dify`.domain.identity.PasswordHasher
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class SpringPasswordHasher(private val encoder: PasswordEncoder) : PasswordHasher {
    override fun encode(password: String) = encoder.encode(password)!!
    override fun matches(password: String, hash: String) = encoder.matches(password, hash)
}
