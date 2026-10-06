package com.thridify.interfaces.rest.dto

import com.thridify.application.service.apikey.create.ApiKeyCreatedResult
import com.thridify.application.service.apikey.list.ApiKeyResult
import com.thridify.application.service.generation.submit.GenerateResult
import com.thridify.application.service.identity.AuthResult
import com.thridify.application.service.job.JobResult
import com.thridify.application.service.job.history.JobHistoryResult
import com.thridify.application.service.plan.PlanResult
import com.thridify.application.service.subscription.checkout.CheckoutResult
import com.thridify.application.service.subscription.portal.PortalResult
import com.thridify.application.service.subscription.status.SubscriptionStatusResult
import com.thridify.application.service.webhook.WebhookResult

fun AuthResult.toResponse() = AuthResponse(token = token, userId = userId, email = email, isAdmin = isAdmin)

fun ApiKeyCreatedResult.toResponse() = ApiKeyCreatedResponse(id = id, key = key, label = label, planName = planName, createdAt = createdAt)

fun ApiKeyResult.toResponse() = ApiKeyResponse(id = id, keyPrefix = keyPrefix, label = label, planName = planName, isActive = isActive, createdAt = createdAt, revokedAt = revokedAt)

fun WebhookResult.toResponse() = WebhookResponse(id = id, url = url, createdAt = createdAt, updatedAt = updatedAt)

fun GenerateResult.toResponse() = GenerateResponse(jobId = jobId, status = status)

fun JobResult.toResponse() = JobResponse(id = id, status = status, inputImage1 = inputImage1, inputImage2 = inputImage2, outputGlbUrl = outputGlbUrl, outputUsdzUrl = outputUsdzUrl, errorMessage = errorMessage, createdAt = createdAt, completedAt = completedAt, inputImages = inputImages, productId = productId, attachmentStatus = attachmentStatus, attachmentError = attachmentError)

fun JobHistoryResult.toResponse() = JobHistoryEntry(id = id, jobId = jobId, status = status, details = details, createdAt = createdAt)

fun PlanResult.toResponse() = PlanResponse(id = id, name = name, displayName = displayName, description = description, priceCents = priceCents, currency = currency, rateLimitRpm = rateLimitRpm, monthlyQuota = monthlyQuota, sortOrder = sortOrder, stripePriceId = stripePriceId, isActive = isActive)

fun SubscriptionStatusResult.toResponse() = SubscriptionStatusResponse(planId = planId, planName = planName, displayName = displayName, priceCents = priceCents, status = status, currentPeriodEnd = currentPeriodEnd, isActive = isActive)

fun CheckoutResult.toResponse() = CheckoutResponse(checkoutUrl = checkoutUrl)

fun PortalResult.toResponse() = PortalResponse(portalUrl = portalUrl)
