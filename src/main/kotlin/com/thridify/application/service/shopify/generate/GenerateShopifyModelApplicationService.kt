package com.thridify.application.service.shopify.generate

import com.thridify.application.service.generation.submit.GenerateResult
import com.thridify.application.service.generation.submit.GenerationImage
import com.thridify.domain.generation.GenerationPolicy
import com.thridify.domain.generation.GenerationTaskPublisher
import com.thridify.domain.generation.ImageStorage
import com.thridify.domain.job.JobHistoryRepository
import com.thridify.domain.shopify.ShopifyAccessPolicy
import com.thridify.domain.shopify.ShopifyAssetDeletionClient
import com.thridify.domain.shopify.ShopifyBillingClient
import com.thridify.domain.shopify.ShopifyGenerationRepository
import com.thridify.domain.shopify.ShopifySessionVerifier
import com.thridify.domain.shopify.ShopifyStoreRepository
import com.thridify.domain.transaction.TransactionProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.UUID

@Service
class GenerateShopifyModelApplicationService(private val sessions: ShopifySessionVerifier, private val stores: ShopifyStoreRepository, private val billing: ShopifyBillingClient, private val generations: ShopifyGenerationRepository, private val storage: ImageStorage, private val history: JobHistoryRepository, private val publisher: GenerationTaskPublisher, private val transactions: TransactionProvider, private val access: ShopifyAccessPolicy, private val images: GenerationPolicy, private val deletion: ShopifyAssetDeletionClient, private val providers: com.thridify.domain.generation.GenerationProviderRegistry, private val products: com.thridify.domain.shopify.ShopifyProductClient, private val attachments: com.thridify.domain.shopify.ShopifyAttachmentRepository, private val productPolicy: com.thridify.domain.shopify.ShopifyProductPolicy) {
    private val log = LoggerFactory.getLogger(javaClass)
    fun execute(command: GenerateShopifyModelCommand): GenerateResult {
        val session = sessions.verify(command.idToken)
        val store = access.connected(stores.findByDomain(session.shopDomain))
        command.productId?.let(productPolicy::productId)
        generations.findRequest(store.billingScopeId, command.requestId)?.let {
            productPolicy.sameProduct(it.productId, command.productId)
            return GenerateResult(it.id, it.status)
        }
        providers.current().validateInputImages(command.imageIds?.size ?: command.images.size)
        val observedAt = OffsetDateTime.now()
        val snapshot = billing.currentSubscription(store.shopId)
        val product = command.productId?.let { products.product(session, command.idToken, it, true) }
        val inputs = if (command.imageIds != null) {
            productPolicy.imageIds(command.imageIds)
            val selectedProduct = product ?: throw com.thridify.shared.exception.BadRequestException("Choose a product before selecting its photos")
            products.downloadPhotos(selectedProduct, command.imageIds).map { GenerationImage(it.data, it.filename, it.contentType) }
        } else {
            command.images
        }
        images.ensureImagesPresent(inputs.map { it.data.size })
        inputs.forEach { access.image(it.data.size, it.contentType) }
        if (product != null) products.prepareBackground(store, command.idToken)
        val previous = transactions.transaction {
            generations.lock(store)
            generations.findRequest(store.billingScopeId, command.requestId).also {
                if (it == null) access.generation(stores.synchronize(store, snapshot, observedAt), OffsetDateTime.now())
            }
        }
        previous?.let {
            productPolicy.sameProduct(it.productId, command.productId)
            return GenerateResult(it.id, it.status)
        }
        val id = UUID.randomUUID()
        val keys = inputs.map { "shopify/${store.billingScopeId}/${images.inputKey(it.filename)}" }
        var retained = false
        try {
            inputs.zip(keys).forEach { (image, key) -> storage.upload(key, image.data, image.contentType) }
            val result = transactions.transaction {
                generations.lock(store)
                val duplicate = generations.findRequest(store.billingScopeId, command.requestId)
                if (duplicate == null) access.generation(stores.synchronize(store, snapshot, observedAt), OffsetDateTime.now())
                val job = generations.insert(store, command.requestId, id, keys)
                if (job.id == id) {
                    command.productId?.let { attachments.bind(store, id, it) }
                    history.insert(id, "PENDING", "Job created through Shopify")
                    publisher.publish(id, keys)
                }
                productPolicy.sameProduct(if (job.id == id) command.productId else job.productId, command.productId)
                GenerateResult(job.id, job.status)
            }
            retained = result.jobId == id
            return result
        } finally {
            if (!retained) {
                for (key in keys) {
                    runCatching { deletion.deleteInput(key) }.onFailure { log.warn("Could not clean up an uncommitted Shopify upload error_type={}", it.javaClass.simpleName) }
                }
            }
        }
    }
}

data class GenerateShopifyModelCommand(val idToken: String, val requestId: UUID, val images: List<GenerationImage>, val productId: String? = null, val imageIds: List<String>? = null) {
    constructor(idToken: String, requestId: UUID, image1: GenerationImage, image2: GenerationImage) : this(idToken, requestId, listOf(image1, image2))
}
