package com.thridify.application.service.shopify.getmodel

import com.thridify.application.service.job.JobResult
import com.thridify.application.service.job.toResult
import com.thridify.domain.shopify.ShopifyAccessPolicy
import com.thridify.domain.shopify.ShopifyGenerationRepository
import com.thridify.domain.shopify.ShopifySessionVerifier
import com.thridify.domain.shopify.ShopifyStoreRepository
import com.thridify.shared.exception.NotFoundException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GetShopifyModelApplicationService(private val sessions: ShopifySessionVerifier, private val stores: ShopifyStoreRepository, private val generations: ShopifyGenerationRepository, private val access: ShopifyAccessPolicy) {
    fun execute(query: GetShopifyModelQuery): JobResult {
        val store = access.connected(stores.findByDomain(sessions.verify(query.idToken).shopDomain))
        return (generations.findJob(store.billingScopeId, query.jobId) ?: throw NotFoundException("Model not found in this store")).toResult()
    }
}

data class GetShopifyModelQuery(val idToken: String, val jobId: UUID)
