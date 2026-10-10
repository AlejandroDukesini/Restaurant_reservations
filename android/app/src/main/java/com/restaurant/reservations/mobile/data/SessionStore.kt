package com.restaurant.reservations.mobile.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class StoredSession(
    val token: String,
    val userId: Long,
    val email: String,
    val role: String,
    val restaurantId: Long?
)

/**
 * Sesion guardada en EncryptedSharedPreferences (clave AES en el Android Keystore).
 * El rol guardado solo sirve para pintar la primera pantalla: al abrir la app se
 * confirma con GET /api/auth/me y el servidor sigue decidiendo cada permiso.
 *
 * Si el Keystore falla en algun dispositivo, la sesion vive solo en memoria: se pide
 * login al reabrir la app, pero el token nunca queda guardado en claro.
 */
class SessionStore(context: Context) {

    private val prefs: SharedPreferences? = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Sin el token en el mensaje: solo el tipo de fallo.
        Log.w(TAG, "Almacen cifrado no disponible; la sesion no se persistira (${e.javaClass.simpleName})")
        null
    }

    private val _session = MutableStateFlow(read())
    val session: StateFlow<StoredSession?> = _session.asStateFlow()

    val current: StoredSession? get() = _session.value

    fun save(session: StoredSession) {
        prefs?.edit()
            ?.putString(KEY_TOKEN, session.token)
            ?.putLong(KEY_USER_ID, session.userId)
            ?.putString(KEY_EMAIL, session.email)
            ?.putString(KEY_ROLE, session.role)
            ?.putLong(KEY_RESTAURANT_ID, session.restaurantId ?: NO_RESTAURANT)
            ?.apply()
        _session.value = session
    }

    fun clear() {
        prefs?.edit()?.clear()?.apply()
        _session.value = null
    }

    private fun read(): StoredSession? {
        val p = prefs ?: return null
        val token = p.getString(KEY_TOKEN, null) ?: return null
        val restaurantId = p.getLong(KEY_RESTAURANT_ID, NO_RESTAURANT)
        return StoredSession(
            token = token,
            userId = p.getLong(KEY_USER_ID, 0),
            email = p.getString(KEY_EMAIL, "") ?: "",
            role = p.getString(KEY_ROLE, "") ?: "",
            restaurantId = restaurantId.takeIf { it != NO_RESTAURANT }
        )
    }

    private companion object {
        const val TAG = "SessionStore"
        const val FILE_NAME = "reservas_session"
        const val KEY_TOKEN = "token"
        const val KEY_USER_ID = "user_id"
        const val KEY_EMAIL = "email"
        const val KEY_ROLE = "role"
        const val KEY_RESTAURANT_ID = "restaurant_id"
        const val NO_RESTAURANT = -1L
    }
}
