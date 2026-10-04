package com.`3dify`.application.service.identity.register

data class RegisterUserCommand(val email: String, val password: String, val name: String? = null)
