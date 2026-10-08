package com.thridify.domain.generation

import java.time.OffsetDateTime

interface PendingInputRepository {
    fun record(keys: List<String>)
    fun release(keys: List<String>)
    fun expired(before: OffsetDateTime): List<String>
}
