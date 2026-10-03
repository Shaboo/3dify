package com.omni3d.interfaces.rest

import com.omni3d.interfaces.rest.dto.AuthResponse
import com.omni3d.interfaces.rest.dto.LoginRequest
import com.omni3d.interfaces.rest.dto.RegisterRequest
import com.omni3d.application.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/auth")
class AuthController(
    private val userService: UserService
) {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@RequestBody request: RegisterRequest): AuthResponse =
        userService.register(request)

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): AuthResponse =
        userService.login(request)
}
