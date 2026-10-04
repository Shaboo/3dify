package com.thridify.application.service.shopify.connect

import com.thridify.domain.shopify.ShopifyAdminClient
import com.thridify.domain.shopify.ShopifySessionVerifier
import com.thridify.domain.shopify.ShopifyStoreRepository
import com.thridify.domain.transaction.TransactionProvider
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ConnectShopifyStoreApplicationService(private val sessions: ShopifySessionVerifier, private val admin: ShopifyAdminClient, private val stores: ShopifyStoreRepository, private val transactions: TransactionProvider) {
    fun execute(command: ConnectShopifyStoreCommand): ShopifyStoreResult {
        val session = sessions.verify(command.idToken)
        val shop = admin.shop(session, command.idToken)
        val store = transactions.transaction { stores.connect(shop) }
        return ShopifyStoreResult(store.workspaceId, store.connectionId, store.billingScopeId, store.shopDomain)
    }
}

data class ConnectShopifyStoreCommand(val idToken: String)
data class ShopifyStoreResult(val workspaceId: UUID, val connectionId: UUID, val billingScopeId: UUID, val shopDomain: String)
