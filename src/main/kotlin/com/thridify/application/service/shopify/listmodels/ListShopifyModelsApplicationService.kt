package com.thridify.application.service.shopify.listmodels

import com.thridify.application.service.job.JobResult
import com.thridify.application.service.job.toResult
import com.thridify.domain.shopify.ShopifyAccessPolicy
import com.thridify.domain.shopify.ShopifyGenerationRepository
import com.thridify.domain.shopify.ShopifySessionVerifier
import com.thridify.domain.shopify.ShopifyStoreRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ListShopifyModelsApplicationService(private val sessions: ShopifySessionVerifier, private val stores: ShopifyStoreRepository, private val generations: ShopifyGenerationRepository, private val access: ShopifyAccessPolicy) {
    fun execute(query: ListShopifyModelsQuery): ShopifyModelPage {
        val store = access.connected(stores.findByDomain(sessions.verify(query.idToken).shopDomain))
        val jobs = generations.listJobs(store.billingScopeId, query.before)
        return ShopifyModelPage(jobs.map { it.toResult() }, jobs.lastOrNull()?.takeIf { jobs.size == 50 }?.id)
    }
}

data class ListShopifyModelsQuery(val idToken: String, val before: UUID?)
data class ShopifyModelPage(val models: List<JobResult>, val nextCursor: UUID?)
