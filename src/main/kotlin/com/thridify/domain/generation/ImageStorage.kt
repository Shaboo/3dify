package com.thridify.domain.generation

interface ImageStorage {
    fun upload(objectKey: String, data: ByteArray, contentType: String): String
}
