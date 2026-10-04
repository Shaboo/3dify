package com.`3dify`.domain.identity

interface PasswordHasher {
    fun encode(password: String): String
    fun matches(password: String, hash: String): Boolean
}
