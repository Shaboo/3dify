package com.thridify.domain.generation

interface ImageStorage {
    fun delete(objectKey: String)
    fun upload(objectKey: String, data: ByteArray, contentType: String): String
}
