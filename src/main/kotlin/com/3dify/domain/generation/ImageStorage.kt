package com.`3dify`.domain.generation

interface ImageStorage {
    fun upload(objectKey: String, data: ByteArray, contentType: String): String
}
