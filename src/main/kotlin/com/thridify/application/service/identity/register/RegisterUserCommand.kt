package com.thridify.application.service.identity.register

data class RegisterUserCommand(val email: String, val password: String, val name: String? = null)
