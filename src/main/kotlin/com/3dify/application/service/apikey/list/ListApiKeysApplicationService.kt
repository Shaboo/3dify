package com.`3dify`.application.service.apikey.list

import com.`3dify`.application.service.apikey.list.ApiKeyResult
import com.`3dify`.domain.apikey.ApiKeyRepository
import org.springframework.stereotype.Service

@Service
class ListApiKeysApplicationService(private val keys: ApiKeyRepository) {
    fun execute(query: ListApiKeysQuery) = keys.findAllByUserId(query.userId).map {
        ApiKeyResult(it.id, it.keyPrefix + "...", it.label, it.planName, it.isActive, it.createdAt.toString(), it.revokedAt?.toString())
    }
}
