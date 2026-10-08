package com.thridify.infrastructure.security

import com.thridify.domain.access.RequestRateLimiter
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.BucketConfiguration
import io.github.bucket4j.distributed.jdbc.BucketTableSettings
import io.github.bucket4j.distributed.jdbc.PrimaryKeyMapper
import io.github.bucket4j.distributed.jdbc.SQLProxyConfiguration
import io.github.bucket4j.distributed.jdbc.SQLProxyConfigurationBuilder
import io.github.bucket4j.distributed.proxy.ProxyManager
import io.github.bucket4j.postgresql.PostgreSQLadvisoryLockBasedProxyManager
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID
import javax.sql.DataSource

@Service
class RateLimiterService(
    dataSource: DataSource,
) : RequestRateLimiter {

    private val proxyManager: ProxyManager<String>

    init {
        val tableSettings = BucketTableSettings.customSettings("rate_limits", "id", "state")
        val proxyConfig: SQLProxyConfiguration<String> = SQLProxyConfigurationBuilder.builder()
            .withPrimaryKeyMapper(PrimaryKeyMapper.STRING)
            .withTableSettings(tableSettings)
            .build(dataSource)

        proxyManager = PostgreSQLadvisoryLockBasedProxyManager<String, Any>(proxyConfig)
    }

    /**
     * Check if the request is allowed under the rate limit.
     * Returns true if within limit, false if exceeded.
     */
    override fun isAllowed(apiKeyId: UUID, maxRequestsPerMinute: Int): Boolean {
        if (maxRequestsPerMinute <= 0) return false

        val key = "$apiKeyId:$maxRequestsPerMinute"
        val configuration = BucketConfiguration.builder()
            .addLimit(
                Bandwidth.builder()
                    .capacity(maxRequestsPerMinute.toLong())
                    .refillIntervally(maxRequestsPerMinute.toLong(), Duration.ofMinutes(1))
                    .build(),
            )
            .build()

        val bucket = proxyManager.builder().build(key) { configuration }
        return bucket.tryConsume(1)
    }
}
