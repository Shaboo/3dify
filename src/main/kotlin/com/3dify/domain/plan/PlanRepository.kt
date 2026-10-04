package com.`3dify`.domain.plan

import com.`3dify`.domain.plan.PlanEntity
import java.util.UUID

interface PlanRepository {
    fun findAll(): List<PlanEntity>
    fun findAllActive(): List<PlanEntity>
    fun findById(id: UUID): PlanEntity?
    fun findByName(name: String): PlanEntity?
    fun insert(
        name: String,
        displayName: String,
        description: String?,
        rateLimitRpm: Int,
        monthlyQuota: Int,
        priceCents: Int,
        currency: String,
        stripePriceId: String?,
        sortOrder: Int,
    ): UUID
    fun update(
        id: UUID,
        displayName: String?,
        description: String?,
        priceCents: Int?,
        rateLimitRpm: Int?,
        monthlyQuota: Int?,
        stripePriceId: String?,
        sortOrder: Int?,
    ): Unit
    fun deactivate(id: UUID): Unit
}
