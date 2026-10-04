package com.thridify.shared.exception

open class ApiException(val statusCode: Int, override val message: String) : RuntimeException(message)
class NotFoundException(message: String) : ApiException(404, message)
class ConflictException(message: String) : ApiException(409, message)
class BadRequestException(message: String) : ApiException(400, message)
class UnauthorizedException(message: String) : ApiException(401, message)
