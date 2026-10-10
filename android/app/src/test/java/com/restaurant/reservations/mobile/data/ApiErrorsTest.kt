package com.restaurant.reservations.mobile.data

import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import kotlinx.serialization.decodeFromString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ApiErrorsTest {

    private fun httpError(code: Int, body: String) =
        HttpException(Response.error<Any>(code, body.toResponseBody("application/json".toMediaType())))

    @Test
    fun usaElMensajeDelFormatoDeErrorDeLaApi() {
        val body = """{"status":409,"error":"Conflict","message":"Table already reserved for this time slot","details":[]}"""
        assertEquals("Table already reserved for this time slot", userMessage(httpError(409, body)))
    }

    @Test
    fun cuerpoSinFormatoConocidoMuestraElCodigo() {
        assertEquals("Error 500", userMessage(httpError(500, "<html>error</html>")))
        assertNull(parseServerMessage(""))
    }

    @Test
    fun cuatrocientosUnoEsSesionExpiradaYCuatrocientosTresSinPermiso() {
        assertEquals(MSG_SESSION_EXPIRED, userMessage(httpError(401, "{}")))
        assertEquals(MSG_FORBIDDEN, userMessage(httpError(403, """{"message":"Access denied"}""")))
    }

    @Test
    fun distingueTiempoDeEsperaDeFalloDeRed() {
        assertEquals(MSG_TIMEOUT, userMessage(InterruptedIOException("timeout")))
        assertEquals(MSG_TIMEOUT, userMessage(SocketTimeoutException()))
        assertEquals(MSG_NETWORK, userMessage(IOException("Unable to resolve host")))
    }

    @Test
    fun ignoraCamposNuevosDelBackend() {
        val user = apiJson.decodeFromString<SessionUser>(
            """{"userId":7,"email":"chef@example.test","role":"COOK","restaurantId":3,"campoNuevo":true}"""
        )
        assertEquals(SessionUser(7, "chef@example.test", "COOK", 3), user)
    }
}
