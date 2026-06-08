package com.restaurant.reservations.controller

import com.restaurant.reservations.exception.BusinessException
import com.restaurant.reservations.exception.ReservationConflictException
import com.restaurant.reservations.exception.ResourceNotFoundException
import jakarta.validation.ConstraintViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

data class ApiErrorResponse(
    val status: Int,
    val error: String,
    val message: String,
    val details: List<String> = emptyList()
)

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleNotFound(exception: ResourceNotFoundException): ResponseEntity<ApiErrorResponse> {
        return error(HttpStatus.NOT_FOUND, exception.message ?: "Resource not found")
    }

    @ExceptionHandler(ReservationConflictException::class)
    fun handleConflict(exception: ReservationConflictException): ResponseEntity<ApiErrorResponse> {
        return error(HttpStatus.CONFLICT, exception.message ?: "Reservation conflict")
    }

    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(exception: BusinessException): ResponseEntity<ApiErrorResponse> {
        return error(HttpStatus.BAD_REQUEST, exception.message ?: "Invalid request")
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(exception: IllegalArgumentException): ResponseEntity<ApiErrorResponse> {
        return error(HttpStatus.BAD_REQUEST, exception.message ?: "Invalid request")
    }

    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleOptimisticLock(exception: OptimisticLockingFailureException): ResponseEntity<ApiErrorResponse> {
        return error(HttpStatus.CONFLICT, "Concurrent update detected. Please retry.")
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(exception: MethodArgumentNotValidException): ResponseEntity<ApiErrorResponse> {
        val details = exception.bindingResult.fieldErrors.map { "${it.field}: ${it.defaultMessage}" }
        return error(HttpStatus.BAD_REQUEST, "Validation failed", details)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(exception: ConstraintViolationException): ResponseEntity<ApiErrorResponse> {
        val details = exception.constraintViolations.map { "${it.propertyPath}: ${it.message}" }
        return error(HttpStatus.BAD_REQUEST, "Validation failed", details)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(exception: Exception): ResponseEntity<ApiErrorResponse> {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, exception.message ?: "Unexpected server error")
    }

    private fun error(
        status: HttpStatus,
        message: String,
        details: List<String> = emptyList()
    ): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity.status(status).body(
            ApiErrorResponse(
                status = status.value(),
                error = status.reasonPhrase,
                message = message,
                details = details
            )
        )
    }
}
