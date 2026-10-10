package com.restaurant.reservations.mobile.data

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** JSON tolerante: los campos que agregue el backend no rompen versiones viejas de la app. */
val apiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

object ApiClient {

    // Mismo limite que el SPA (REQUEST_TIMEOUT_MS): ninguna pantalla queda cargando sin fin.
    private const val TIMEOUT_SECONDS = 15L

    fun create(baseUrl: String, sessionStore: SessionStore): ApiService {
        val authInterceptor = Interceptor { chain ->
            val token = sessionStore.current?.token
            val request = if (token != null) {
                chain.request().newBuilder().header("Authorization", "Bearer $token").build()
            } else {
                chain.request()
            }
            val response = chain.proceed(request)
            // Solo un 401 de una peticion autenticada significa "sesion caducada o revocada".
            // En el login (sin token) el 401 es "credenciales invalidas" y lo maneja la pantalla.
            if (response.code == 401 && token != null) {
                sessionStore.clear()
            }
            response
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(apiJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }
}
