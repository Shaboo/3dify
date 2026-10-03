package com.omni3d.api.controller

import com.omni3d.api.model.dto.AuthResponse
import com.omni3d.api.model.dto.LoginRequest
import com.omni3d.api.model.dto.RegisterRequest
import com.omni3d.api.service.UserService
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
