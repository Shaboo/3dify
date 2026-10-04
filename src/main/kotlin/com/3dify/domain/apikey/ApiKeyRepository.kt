package com.`3dify`.domain.apikey

import com.`3dify`.domain.apikey.ApiKeyAuthEntity
import com.`3dify`.domain.apikey.ApiKeyWithPlanEntity
import java.util.UUID

interface ApiKeyRepository {
    fun insert(id: UUID, userId: UUID, planId: UUID, keyHash: String, keyPrefix: String, label: String?): Unit
    fun findAllByUserId(userId: UUID): List<ApiKeyWithPlanEntity>
    fun revoke(userId: UUID, keyId: UUID): Int
    fun findByKeyHash(hash: String): ApiKeyAuthEntity?
    fun updatePlanForUser(userId: UUID, planId: UUID): Unit
    fun setActiveByUserId(userId: UUID, active: Boolean): Unit
}
