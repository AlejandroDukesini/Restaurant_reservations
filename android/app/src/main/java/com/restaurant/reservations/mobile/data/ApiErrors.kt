package com.restaurant.reservations.mobile.data

import java.io.IOException
import java.io.InterruptedIOException
import retrofit2.HttpException
import retrofit2.Response

const val MSG_SESSION_EXPIRED = "Sesión expirada. Inicia sesión de nuevo."
const val MSG_TIMEOUT = "El servidor tardó demasiado en responder. Inténtalo de nuevo."
const val MSG_NETWORK = "No se pudo conectar con el servidor. Revisa tu conexión e inténtalo de nuevo."
const val MSG_FORBIDDEN = "No tienes permiso para esta acción."

/** Mensaje del cuerpo de error de la API ({"message": ...}), o null si no es ese formato. */
fun parseServerMessage(body: String?): String? {
    if (body.isNullOrBlank()) return null
    return try {
        apiJson.decodeFromString(ApiErrorBody.serializer(), body).message?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }
}

/** Traduce cualquier fallo de una llamada a un mensaje para la persona usuaria (mismos textos que la web). */
fun userMessage(error: Throwable): String = when (error) {
    is HttpException -> when (error.code()) {
        401 -> MSG_SESSION_EXPIRED
        403 -> MSG_FORBIDDEN
        else -> parseServerMessage(error.response()?.errorBody()?.string()) ?: "Error ${error.code()}"
    }
    // OkHttp senala el callTimeout con InterruptedIOException (SocketTimeoutException es subclase).
    is InterruptedIOException -> MSG_TIMEOUT
    is IOException -> MSG_NETWORK
    else -> error.message ?: "Error inesperado"
}

/** Convierte una respuesta sin cuerpo (204) que no fue exitosa en la misma excepcion que lanza Retrofit. */
fun Response<Unit>.requireSuccess() {
    if (!isSuccessful) throw HttpException(this)
}
