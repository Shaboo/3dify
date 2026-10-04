package com.thridify.interfaces.rest

import com.thridify.application.service.apikey.create.CreateApiKeyApplicationService
import com.thridify.application.service.apikey.create.CreateApiKeyCommand
import com.thridify.application.service.apikey.list.ListApiKeysApplicationService
import com.thridify.application.service.apikey.list.ListApiKeysQuery
import com.thridify.application.service.apikey.revoke.RevokeApiKeyApplicationService
import com.thridify.application.service.apikey.revoke.RevokeApiKeyCommand
import com.thridify.interfaces.rest.dto.CreateApiKeyRequest
import com.thridify.interfaces.rest.dto.toResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/dashboard/api-keys")
class ApiKeyController(
    private val createKey: CreateApiKeyApplicationService,
    private val listKeys: ListApiKeysApplicationService,
    private val revokeKey: RevokeApiKeyApplicationService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createKey(authentication: Authentication, @RequestBody request: CreateApiKeyRequest) = createKey.execute(CreateApiKeyCommand(UUID.fromString(authentication.principal as String), request.label, request.planName)).toResponse()

    @GetMapping
    fun listKeys(authentication: Authentication) = listKeys.execute(ListApiKeysQuery(UUID.fromString(authentication.principal as String))).map { it.toResponse() }

    @DeleteMapping("/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revokeKey(authentication: Authentication, @PathVariable keyId: UUID) = revokeKey.execute(RevokeApiKeyCommand(UUID.fromString(authentication.principal as String), keyId))
}
