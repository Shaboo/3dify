package com.omni3d.api.service

import com.omni3d.api.domain.PlanEntity
import com.omni3d.api.exception.NotFoundException
import com.omni3d.api.model.dto.PlanResponse
import com.omni3d.api.repository.PlanRepository
import com.stripe.model.Price
import com.stripe.model.Product
import com.stripe.param.PriceCreateParams
import com.stripe.param.ProductCreateParams
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class PlanService(private val planRepository: PlanRepository) {

    fun listAllActive(): List<PlanResponse> = planRepository.findAllActive().map { it.toResponse() }
    fun listAll(): List<PlanResponse>        = planRepository.findAll().map { it.toResponse() }

    fun getById(id: UUID): PlanResponse =
        planRepository.findById(id)?.toResponse()
            ?: throw NotFoundException("Plan $id not found")

    fun createPlan(
        name: String,
        displayName: String,
        description: String?,
        rateLimitRpm: Int,
        monthlyQuota: Int,
        priceCents: Int,
        currency: String,
        sortOrder: Int
    ): PlanResponse {
        val stripePriceId = if (priceCents > 0) createStripePrice(name, displayName, priceCents, currency) else null
        val id = planRepository.insert(
            name          = name,
            displayName   = displayName,
            description   = description,
            rateLimitRpm  = rateLimitRpm,
            monthlyQuota  = monthlyQuota,
            priceCents    = priceCents,
            currency      = currency,
            stripePriceId = stripePriceId,
            sortOrder     = sortOrder
        )
        return planRepository.findById(id)?.toResponse()
            ?: throw IllegalStateException("Failed to retrieve created plan")
    }

    fun updatePlan(
        id: UUID,
        displayName: String?,
        description: String?,
        priceCents: Int?,
        rateLimitRpm: Int?,
        monthlyQuota: Int?,
        sortOrder: Int?
    ): PlanResponse {
        planRepository.findById(id) ?: throw NotFoundException("Plan $id not found")
        planRepository.update(id, displayName, description, priceCents, rateLimitRpm, monthlyQuota, null, sortOrder)
        return planRepository.findById(id)?.toResponse()
            ?: throw IllegalStateException("Plan not found after update")
    }

    fun deactivatePlan(id: UUID) {
        planRepository.findById(id) ?: throw NotFoundException("Plan $id not found")
        planRepository.deactivate(id)
    }

    private fun createStripePrice(name: String, displayName: String, priceCents: Int, currency: String): String {
        val product = Product.create(ProductCreateParams.builder().setName(displayName).build())
        val price   = Price.create(
            PriceCreateParams.builder()
                .setProduct(product.id)
                .setUnitAmount(priceCents.toLong())
                .setCurrency(currency)
                .setRecurring(PriceCreateParams.Recurring.builder()
                    .setInterval(PriceCreateParams.Recurring.Interval.MONTH)
                    .build())
                .build()
        )
        return price.id
    }
}

private fun PlanEntity.toResponse() = PlanResponse(
    id            = id,
    name          = name,
    displayName   = displayName,
    description   = description,
    priceCents    = priceCents,
    currency      = currency,
    rateLimitRpm  = rateLimitRpm,
    monthlyQuota  = monthlyQuota,
    sortOrder     = sortOrder,
    stripePriceId = stripePriceId,
    isActive      = isActive
)
