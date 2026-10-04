package com.`3dify`.application.service.plan

import com.`3dify`.application.service.plan.PlanResult
import com.`3dify`.domain.plan.PlanEntity

internal fun PlanEntity.toResult() = PlanResult(
    id, name, displayName, description, priceCents, currency,
    rateLimitRpm, monthlyQuota, sortOrder, stripePriceId, isActive,
)
