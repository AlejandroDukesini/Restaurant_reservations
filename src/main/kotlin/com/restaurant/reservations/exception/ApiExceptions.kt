package com.restaurant.reservations.exception

open class BusinessException(message: String) : RuntimeException(message)

class ResourceNotFoundException(message: String) : BusinessException(message)

class ReservationConflictException(message: String) : BusinessException(message)

class ValidationBusinessException(message: String) : BusinessException(message)
