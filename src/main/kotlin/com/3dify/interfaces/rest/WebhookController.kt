package com.`3dify`.interfaces.rest

import com.`3dify`.application.service.webhook.delete.DeleteWebhookApplicationService
import com.`3dify`.application.service.webhook.delete.DeleteWebhookCommand
import com.`3dify`.application.service.webhook.get.GetWebhookApplicationService
import com.`3dify`.application.service.webhook.get.GetWebhookQuery
import com.`3dify`.application.service.webhook.set.SetWebhookApplicationService
import com.`3dify`.application.service.webhook.set.SetWebhookCommand
import com.`3dify`.interfaces.rest.dto.SetWebhookRequest
import com.`3dify`.interfaces.rest.dto.toResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/dashboard/webhooks")
class WebhookController(
    private val getWebhook: GetWebhookApplicationService,
    private val setWebhook: SetWebhookApplicationService,
    private val deleteWebhook: DeleteWebhookApplicationService,
) {
    @GetMapping
    fun getWebhook(authentication: Authentication) = getWebhook.execute(GetWebhookQuery(UUID.fromString(authentication.principal as String))).toResponse()

    @PutMapping
    fun setWebhook(authentication: Authentication, @RequestBody request: SetWebhookRequest) = setWebhook.execute(SetWebhookCommand(UUID.fromString(authentication.principal as String), request.url)).toResponse()

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteWebhook(authentication: Authentication) = deleteWebhook.execute(DeleteWebhookCommand(UUID.fromString(authentication.principal as String)))
}
