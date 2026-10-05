package com.restaurant.reservations.controller

import com.restaurant.reservations.exception.BusinessException
import com.restaurant.reservations.exception.ReservationConflictException
import com.restaurant.reservations.exception.ResourceNotFoundException
import jakarta.validation.ConstraintViolationException
<<<<<<< HEAD
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
=======
import org.slf4j.LoggerFactory
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.util.UUID
>>>>>>> ed340704ed016e6dcc9e8c59c76220b3ee9c292e

data class ApiErrorResponse(
    val status: Int,
    val error: String,
    val message: String,
    val details: List<String> = emptyList()
)

@RestControllerAdvice
class GlobalExceptionHandler {
<<<<<<< HEAD
=======

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    // 403 de @PreAuthorize: es la senal de que alguien con sesion valida intento
    // usar un endpoint fuera de su rol. Se registra y se responde sin detalle.
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(exception: AccessDeniedException): ResponseEntity<ApiErrorResponse> {
        log.warn("Acceso denegado por autorizacion: {}", exception.message)
        return error(HttpStatus.FORBIDDEN, "Access denied")
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(exception: AuthenticationException): ResponseEntity<ApiErrorResponse> {
        // Mensaje generico e identico para usuario inexistente y clave incorrecta:
        // distinguirlos permite enumerar cuentas validas.
        return error(HttpStatus.UNAUTHORIZED, "Invalid credentials")
    }

>>>>>>> ed340704ed016e6dcc9e8c59c76220b3ee9c292e
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

<<<<<<< HEAD
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(exception: Exception): ResponseEntity<ApiErrorResponse> {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, exception.message ?: "Unexpected server error")
=======
    // Errores del cliente que Spring lanza antes de llegar al controlador. Sin estos
    // handlers caian en handleUnexpected: respondian 500 y se registraban como error
    // del servidor (JSON mal formado, parametro ausente, id no numerico, ruta inexistente).
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(exception: HttpMessageNotReadableException): ResponseEntity<ApiErrorResponse> {
        return error(HttpStatus.BAD_REQUEST, "Malformed or incomplete request body")
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParameter(exception: MissingServletRequestParameterException): ResponseEntity<ApiErrorResponse> {
        return error(HttpStatus.BAD_REQUEST, "Missing required parameter: ${exception.parameterName}")
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(exception: MethodArgumentTypeMismatchException): ResponseEntity<ApiErrorResponse> {
        return error(HttpStatus.BAD_REQUEST, "Invalid value for parameter: ${exception.name}")
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(exception: HttpRequestMethodNotSupportedException): ResponseEntity<ApiErrorResponse> {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed")
    }

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResource(exception: NoResourceFoundException): ResponseEntity<ApiErrorResponse> {
        return error(HttpStatus.NOT_FOUND, "Resource not found")
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(exception: Exception): ResponseEntity<ApiErrorResponse> {
        // El mensaje de una excepcion no controlada suele contener SQL, rutas del
        // sistema o nombres de clase; se queda en el log del servidor y al cliente
        // solo le llega un identificador para correlacionar la incidencia.
        val errorId = UUID.randomUUID().toString()
        log.error("Error no controlado [{}]", errorId, exception)
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error (ref: $errorId)")
>>>>>>> ed340704ed016e6dcc9e8c59c76220b3ee9c292e
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
