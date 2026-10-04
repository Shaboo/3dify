package com.thridify.interfaces.rest

import com.thridify.application.service.identity.login.LoginUserApplicationService
import com.thridify.application.service.identity.login.LoginUserCommand
import com.thridify.application.service.identity.register.RegisterUserApplicationService
import com.thridify.application.service.identity.register.RegisterUserCommand
import com.thridify.interfaces.rest.dto.LoginRequest
import com.thridify.interfaces.rest.dto.RegisterRequest
import com.thridify.interfaces.rest.dto.toResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(private val registerUser: RegisterUserApplicationService, private val loginUser: LoginUserApplicationService) {
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@RequestBody request: RegisterRequest) = registerUser.execute(RegisterUserCommand(request.email, request.password, request.name)).toResponse()

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest) = loginUser.execute(LoginUserCommand(request.email, request.password)).toResponse()
}
