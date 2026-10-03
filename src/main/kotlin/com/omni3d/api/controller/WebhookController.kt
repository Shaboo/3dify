package com.omni3d.api.controller

import com.omni3d.api.model.dto.SetWebhookRequest
import com.omni3d.api.model.dto.WebhookResponse
import com.omni3d.api.service.WebhookService
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/dashboard/webhooks")
class WebhookController(
    private val webhookService: WebhookService
) {

    @GetMapping
    fun getWebhook(authentication: Authentication): WebhookResponse {
        val userId = UUID.fromString(authentication.principal as String)
        return webhookService.getWebhook(userId)
            ?: throw com.omni3d.api.exception.NotFoundException("No webhook configured")
    }

    @PutMapping
    fun setWebhook(
        authentication: Authentication,
        @RequestBody request: SetWebhookRequest
    ): WebhookResponse {
        val userId = UUID.fromString(authentication.principal as String)
        return webhookService.setWebhook(userId, request.url)
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteWebhook(authentication: Authentication) {
        val userId = UUID.fromString(authentication.principal as String)
        webhookService.deleteWebhook(userId)
    }
}
