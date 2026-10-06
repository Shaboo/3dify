package com.thridify.interfaces.rest.dto

import com.thridify.application.service.generation.submit.GenerationImage
import com.thridify.shared.exception.BadRequestException
import org.springframework.web.multipart.MultipartFile

internal fun generationImages(images: List<MultipartFile>?, image1: MultipartFile?, image2: MultipartFile?): List<GenerationImage> {
    if (images != null && (image1 != null || image2 != null)) throw BadRequestException("Use images or the legacy image1/image2 pair, not both")
    val files = images ?: if (image1 != null && image2 != null) listOf(image1, image2) else throw BadRequestException("Provide photos in images")
    return files.map { GenerationImage(it.bytes, it.originalFilename, it.contentType ?: "application/octet-stream") }
}
