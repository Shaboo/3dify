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
class GenerateShopifyModelApplicationService(private val sessions: ShopifySessionVerifier, private val stores: ShopifyStoreRepository, private val billing: ShopifyBillingClient, private val generations: ShopifyGenerationRepository, private val storage: ImageStorage, private val history: JobHistoryRepository, private val publisher: GenerationTaskPublisher, private val transactions: TransactionProvider, private val access: ShopifyAccessPolicy, private val images: GenerationPolicy, private val deletion: ShopifyAssetDeletionClient) {
    private val log = LoggerFactory.getLogger(javaClass)
    fun execute(command: GenerateShopifyModelCommand): GenerateResult {
        val store = access.connected(stores.findByDomain(sessions.verify(command.idToken).shopDomain))
        generations.findRequest(store.billingScopeId, command.requestId)?.let { return GenerateResult(it.id, it.status) }
        images.ensureImagesPresent(command.image1.data.size, command.image2.data.size)
        access.image(command.image1.data.size, command.image1.contentType)
        access.image(command.image2.data.size, command.image2.contentType)
        val observedAt = OffsetDateTime.now()
        val snapshot = billing.currentSubscription(store.shopId)
        val previous = transactions.transaction {
            generations.lock(store)
            generations.findRequest(store.billingScopeId, command.requestId).also {
                if (it == null) access.generation(stores.synchronize(store, snapshot, observedAt), OffsetDateTime.now())
            }
        }
        previous?.let { return GenerateResult(it.id, it.status) }
        val id = UUID.randomUUID()
        val first = "shopify/${store.billingScopeId}/${images.inputKey(command.image1.filename)}"
        val second = "shopify/${store.billingScopeId}/${images.inputKey(command.image2.filename)}"
        var retained = false
        try {
            storage.upload(first, command.image1.data, command.image1.contentType)
            storage.upload(second, command.image2.data, command.image2.contentType)
            val result = transactions.transaction {
                generations.lock(store)
                val duplicate = generations.findRequest(store.billingScopeId, command.requestId)
                if (duplicate == null) access.generation(stores.synchronize(store, snapshot, observedAt), OffsetDateTime.now())
                val job = generations.insert(store, command.requestId, id, first, second)
                if (job.id == id) {
                    history.insert(id, "PENDING", "Job created through Shopify")
                    publisher.publish(id, first, second)
                }
                GenerateResult(job.id, job.status)
            }
            retained = result.jobId == id
            return result
        } finally {
            if (!retained) {
                for (key in listOf(first, second)) {
                    runCatching { deletion.deleteInput(key) }.onFailure { log.warn("Could not clean up an uncommitted Shopify upload error_type={}", it.javaClass.simpleName) }
                }
            }
        }
    }
}

data class GenerateShopifyModelCommand(val idToken: String, val requestId: UUID, val image1: GenerationImage, val image2: GenerationImage)
