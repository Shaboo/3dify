package com.thridify.infrastructure.observability

import com.thridify.shared.exception.ApiException
import io.micrometer.core.instrument.LongTaskTimer
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation
import org.slf4j.LoggerFactory
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator
import org.springframework.aop.support.DefaultPointcutAdvisor
import org.springframework.aop.support.StaticMethodMatcherPointcut
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit

/** Observes bean boundary calls without logging arguments, return values or exception messages. */
class OperationInterceptor(private val registries: ObjectProvider<MeterRegistry>) : MethodInterceptor {
    private val log = LoggerFactory.getLogger("com.thridify.observability.operations")

    override fun invoke(invocation: MethodInvocation): Any? {
        val type = org.springframework.util.ClassUtils.getUserClass(requireNotNull(invocation.`this`))
        val layer = when {
            type.name.contains(".application.") -> "application"
            type.name.contains(".interfaces.") -> "background"
            else -> "dependency"
        }
        val operation = "${type.simpleName}.${invocation.method.name}"
        val started = System.nanoTime()
        val registry = registries.getObject()
        val active = LongTaskTimer.builder("omni3d.operations.active")
            .description("Active bean boundary calls and time spent in unfinished calls")
            .tags("layer", layer, "operation", operation).register(registry).start()
        var outcome = "success"
        try {
            return invocation.proceed()
        } catch (ex: Throwable) {
            outcome = if (ex is ApiException && ex.statusCode in 400..499) "rejected" else "error"
            // Exception messages and stack traces from SDKs can contain credentials or signed URLs.
            if (outcome == "error") {
                log.error("operation_failed layer={} operation={} error_type={} error_at={}", layer, operation, ex.javaClass.simpleName, ex.stackTrace.firstOrNull())
            } else {
                log.debug("operation_rejected layer={} operation={} status={}", layer, operation, (ex as ApiException).statusCode)
            }
            throw ex
        } finally {
            val elapsed = System.nanoTime() - started
            active.stop()
            Timer.builder("omni3d.operations.duration")
                .description("Bean boundary duration; success means returned normally, not business completion")
                .tags("layer", layer, "operation", operation, "outcome", outcome)
                .publishPercentileHistogram(layer == "application")
                .register(registry).record(elapsed, TimeUnit.NANOSECONDS)
            if (layer == "application" && !operation.startsWith("Reconcile") && !operation.startsWith("Authenticate") && !operation.startsWith("Authorize")) {
                log.info("operation_finished operation={} outcome={} duration_ms={}", operation, outcome, elapsed / 1_000_000)
            } else {
                log.debug("operation_finished layer={} operation={} outcome={} duration_ms={}", layer, operation, outcome, elapsed / 1_000_000)
            }
        }
    }
}

@Configuration(proxyBeanMethods = false)
class OperationObservability {
    @Bean
    fun operationAdvisor(registries: ObjectProvider<MeterRegistry>): DefaultPointcutAdvisor = DefaultPointcutAdvisor(
        object : StaticMethodMatcherPointcut() {
            override fun matches(method: Method, targetClass: Class<*>): Boolean {
                if (method.declaringClass == Any::class.java || method.isSynthetic || method.name.contains('$')) return false
                if (method.parameterCount == 0 && targetClass.declaredFields.any { "get${it.name.replaceFirstChar(Char::uppercaseChar)}" == method.name }) return false
                val name = targetClass.name
                return (name.startsWith("com.thridify.application.service.") && name.endsWith("ApplicationService") && method.name == "execute") ||
                    (name.startsWith("com.thridify.interfaces.scheduled.") && method.name == "run") ||
                    (name == "com.thridify.interfaces.messaging.TaskWorker" && method.name == "handleTask") ||
                    (
                        name.startsWith("com.thridify.infrastructure.") && (
                            targetClass.simpleName.endsWith("Repository") || targetClass.simpleName.endsWith("Client") || targetClass.simpleName.endsWith("Verifier") ||
                                targetClass.simpleName.endsWith("Storage") || targetClass.simpleName in setOf("StorageService", "GenerationAssetDownloader", "RateLimiterService", "JwtService", "SpringPasswordHasher", "SpringTransactionProvider", "TaskProducer", "OutboxRelay", "OutboxPublisher", "RabbitGenerationTaskDelivery", "OutboxGenerationTaskPublisher")
                            )
                        )
            }
        },
        OperationInterceptor(registries),
    )

    companion object {
        @Bean
        @JvmStatic
        fun operationAutoProxyCreator() = DefaultAdvisorAutoProxyCreator().apply { isProxyTargetClass = true }
    }
}
