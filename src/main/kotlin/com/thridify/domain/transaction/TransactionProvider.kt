package com.thridify.domain.transaction

interface TransactionProvider {
    fun <T> transaction(action: () -> T): T
}
