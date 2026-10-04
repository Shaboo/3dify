package com.`3dify`.domain.transaction

interface TransactionProvider {
    fun <T> transaction(action: () -> T): T
}
