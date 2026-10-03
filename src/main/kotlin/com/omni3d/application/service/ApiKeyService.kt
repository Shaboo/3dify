package com.omni3d.application.service

import com.omni3d.shared.exception.NotFoundException
import com.omni3d.shared.metrics.AppMetrics
import com.omni3d.interfaces.rest.dto.ApiKeyCreatedResponse
import com.omni3d.interfaces.rest.dto.ApiKeyResponse
import com.omni3d.infrastructure.persistence.ApiKeyRepository
import com.omni3d.infrastructure.persistence.PlanRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.util.UUID

@Service
class ApiKeyService(
    private val apiKeyRepository: ApiKeyRepository,
    private val planRepository: PlanRepository,
    private val metrics: AppMetrics
) {
    private val log = LoggerFactory.getLogger(ApiKeyService::class.java)

    companion object {
        private const val KEY_PREFIX    = "omni_pk_"
        private const val RANDOM_LENGTH = 40
        private val SECURE_RANDOM       = SecureRandom()
    }

    data class ValidatedApiKey(
        val id: UUID,
        val userId: UUID,
        val rateLimitRpm: Int
    )

    fun generateKey(userId: UUID, label: String?, planName: String): ApiKeyCreatedResponse {
        log.info("Generating API key [userId={}, plan={}]", userId, planName)

        val plan = planRepository.findByName(planName)
            ?: run {
                log.warn("API key generation failed -- plan not found [plan={}]", planName)
                throw NotFoundException("Plan '$planName' not found")
            }

        val rawKey = KEY_PREFIX + generateRandomString(RANDOM_LENGTH)
        val hash   = sha256(rawKey)
        val prefix = rawKey.take(16)
        val keyId  = UUID.randomUUID()

        apiKeyRepository.insert(
            id        = keyId,
            userId    = userId,
            planId    = plan.id,
            keyHash   = hash,
            keyPrefix = prefix,
            label     = label
        )

        metrics.apiKeysCreated.increment()
        log.info("API key created [keyId={}, userId={}, plan={}]", keyId, userId, planName)

        return ApiKeyCreatedResponse(
            id        = keyId,
            key       = rawKey,
            label     = label,
            planName  = planName,
            createdAt = OffsetDateTime.now().toString()
        )
    }

    fun listKeys(userId: UUID): List<ApiKeyResponse> =
        apiKeyRepository.findAllByUserId(userId)
            .map { k -> ApiKeyResponse(
                id        = k.id,
                keyPrefix = k.keyPrefix + "...",
                label     = k.label,
                planName  = k.planName,
                isActive  = k.isActive,
                createdAt = k.createdAt.toString(),
                revokedAt = k.revokedAt?.toString()
            )}

    fun revokeKey(userId: UUID, keyId: UUID) {
        log.info("Revoking API key [keyId={}, userId={}]", keyId, userId)
        val updated = apiKeyRepository.revoke(userId, keyId)
        if (updated == 0) {
            log.warn("Key revocation failed -- not found [keyId={}, userId={}]", keyId, userId)
            throw NotFoundException("API key not found")
        }
        metrics.apiKeysRevoked.increment()
        log.info("API key revoked [keyId={}]", keyId)
    }

    fun validateRawKey(rawKey: String): ValidatedApiKey? {
        if (!rawKey.startsWith(KEY_PREFIX)) {
            log.debug("Key validation failed -- invalid prefix")
            metrics.apiKeyValidationsFailed.increment()
            return null
        }

        val auth = apiKeyRepository.findByKeyHash(sha256(rawKey))
        if (auth == null) {
            log.debug("Key validation failed -- hash not found")
            metrics.apiKeyValidationsFailed.increment()
            return null
        }
        if (!auth.isActive) {
            log.debug("Key validation failed -- key inactive [keyId={}]", auth.id)
            metrics.apiKeyValidationsFailed.increment()
            return null
        }

        metrics.apiKeyValidations.increment()
        return ValidatedApiKey(
            id           = auth.id,
            userId       = auth.userId,
            rateLimitRpm = auth.rateLimitRpm
        )
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun generateRandomString(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length).map { chars[SECURE_RANDOM.nextInt(chars.length)] }.joinToString("")
    }
}
