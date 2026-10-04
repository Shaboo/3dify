package com.thridify.interfaces.rest.shopify

import com.thridify.application.service.generation.submit.GenerationImage
import com.thridify.application.service.shopify.connect.ConnectShopifyStoreApplicationService
import com.thridify.application.service.shopify.connect.ConnectShopifyStoreCommand
import com.thridify.application.service.shopify.generate.GenerateShopifyModelApplicationService
import com.thridify.application.service.shopify.generate.GenerateShopifyModelCommand
import com.thridify.application.service.shopify.getmodel.GetShopifyModelApplicationService
import com.thridify.application.service.shopify.getmodel.GetShopifyModelQuery
import com.thridify.application.service.shopify.listmodels.ListShopifyModelsApplicationService
import com.thridify.application.service.shopify.listmodels.ListShopifyModelsQuery
import com.thridify.application.service.shopify.subscription.GetShopifySubscriptionApplicationService
import com.thridify.application.service.shopify.subscription.GetShopifySubscriptionQuery
import com.thridify.interfaces.rest.dto.JobResponse
import com.thridify.interfaces.rest.dto.toResponse
import com.thridify.shared.exception.UnauthorizedException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/shopify/api")
class ShopifyController(private val connect: ConnectShopifyStoreApplicationService, private val subscription: GetShopifySubscriptionApplicationService, private val generate: GenerateShopifyModelApplicationService, private val models: ListShopifyModelsApplicationService, private val model: GetShopifyModelApplicationService) {
    @PostMapping("/connection")
    fun connect(@RequestHeader(value = "Authorization", defaultValue = "") authorization: String): ShopifyConnectionResponse {
        val result = connect.execute(ConnectShopifyStoreCommand(idToken(authorization)))
        return ShopifyConnectionResponse(result.workspaceId, result.connectionId, result.billingScopeId, result.shopDomain)
    }

    @GetMapping("/subscription")
    fun subscription(@RequestHeader(value = "Authorization", defaultValue = "") authorization: String): ShopifySubscriptionResponse {
        val result = subscription.execute(GetShopifySubscriptionQuery(idToken(authorization)))
        return ShopifySubscriptionResponse(result.status, result.planName, result.periodEnd, result.generationLimit, result.generationsConsumed, result.pricingUrl)
    }

    @PostMapping("/models")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun generate(@RequestHeader(value = "Authorization", defaultValue = "") authorization: String, @RequestHeader("Idempotency-Key") requestId: UUID, @RequestPart("image1") image1: MultipartFile, @RequestPart("image2") image2: MultipartFile) = generate.execute(GenerateShopifyModelCommand(idToken(authorization), requestId, image1.toInput(), image2.toInput())).toResponse()

    @GetMapping("/models")
    fun models(@RequestHeader(value = "Authorization", defaultValue = "") authorization: String, @RequestParam(required = false) before: UUID?): ShopifyModelsResponse {
        val result = models.execute(ListShopifyModelsQuery(idToken(authorization), before))
        return ShopifyModelsResponse(result.models.map { it.toResponse() }, result.nextCursor)
    }

    @GetMapping("/models/{jobId}")
    fun model(@RequestHeader(value = "Authorization", defaultValue = "") authorization: String, @PathVariable jobId: UUID) = model.execute(GetShopifyModelQuery(idToken(authorization), jobId)).toResponse()

    private fun idToken(header: String): String = header.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")?.takeIf { it.isNotBlank() } ?: throw UnauthorizedException("A Shopify ID token is required")
    private fun MultipartFile.toInput() = GenerationImage(bytes, originalFilename, contentType ?: "application/octet-stream")
}

data class ShopifyConnectionResponse(val workspaceId: UUID, val connectionId: UUID, val billingScopeId: UUID, val shopDomain: String)
data class ShopifySubscriptionResponse(val status: String, val planName: String?, val periodEnd: String?, val generationLimit: Int, val generationsConsumed: Int, val pricingUrl: String)
data class ShopifyModelsResponse(val models: List<JobResponse>, val nextCursor: UUID?)
