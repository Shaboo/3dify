package com.thridify.application.service.plan

import com.thridify.application.service.plan.PlanResult
import com.thridify.domain.plan.PlanEntity

internal fun PlanEntity.toResult() = PlanResult(
    id, name, displayName, description, priceCents, currency,
    rateLimitRpm, monthlyQuota, sortOrder, stripePriceId, isActive,
)
