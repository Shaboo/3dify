package com.omni3d.interfaces.rest

import com.omni3d.interfaces.rest.dto.ApiKeyCreatedResponse
import com.omni3d.interfaces.rest.dto.ApiKeyResponse
import com.omni3d.interfaces.rest.dto.CreateApiKeyRequest
import com.omni3d.application.service.ApiKeyService
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/dashboard/api-keys")
class ApiKeyController(
    private val apiKeyService: ApiKeyService
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createKey(
        authentication: Authentication,
        @RequestBody request: CreateApiKeyRequest
    ): ApiKeyCreatedResponse {
        val userId = UUID.fromString(authentication.principal as String)
        return apiKeyService.generateKey(userId, request.label, request.planName)
    }

    @GetMapping
    fun listKeys(authentication: Authentication): List<ApiKeyResponse> {
        val userId = UUID.fromString(authentication.principal as String)
        return apiKeyService.listKeys(userId)
    }

    @DeleteMapping("/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revokeKey(
        authentication: Authentication,
        @PathVariable keyId: UUID
    ) {
        val userId = UUID.fromString(authentication.principal as String)
        apiKeyService.revokeKey(userId, keyId)
    }
}
