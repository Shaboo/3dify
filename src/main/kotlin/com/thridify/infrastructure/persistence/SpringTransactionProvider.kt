package com.thridify.infrastructure.persistence

import com.thridify.domain.transaction.TransactionProvider
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Component
class SpringTransactionProvider(manager: PlatformTransactionManager) : TransactionProvider {
    private val template = TransactionTemplate(manager)
    override fun <T> transaction(action: () -> T): T {
        // Failed use cases must not commit partial writes.
        var result: T? = null
        var checkedFailure: Exception? = null
        template.executeWithoutResult {
            try {
                result = action()
            } catch (ex: RuntimeException) {
                throw ex
            } catch (ex: Exception) {
                it.setRollbackOnly()
                checkedFailure = ex
            }
        }
        checkedFailure?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}
