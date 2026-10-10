package com.restaurant.reservations.mobile.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restaurant.reservations.mobile.data.ApiService
import com.restaurant.reservations.mobile.data.LoginRequest
import com.restaurant.reservations.mobile.data.SessionStore
import com.restaurant.reservations.mobile.data.SessionUser
import com.restaurant.reservations.mobile.data.StoredSession
import com.restaurant.reservations.mobile.data.userMessage
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

sealed interface SessionState {
    /** Confirmando con el servidor el token guardado. */
    data object Checking : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val user: SessionUser) : SessionState
    /** No se pudo confirmar (red/timeout): se conserva la sesion para reintentar. */
    data class Error(val message: String) : SessionState
}

class SessionViewModel(
    private val api: ApiService,
    private val store: SessionStore
) : ViewModel() {

    private val _state = MutableStateFlow<SessionState>(
        if (store.current != null) SessionState.Checking else SessionState.SignedOut
    )
    val state: StateFlow<SessionState> = _state.asStateFlow()

    init {
        if (store.current != null) verify()
        // Un 401 en cualquier pantalla borra la sesion (ApiClient): se vuelve al login.
        viewModelScope.launch {
            store.session.collect { session ->
                if (session == null && _state.value !is SessionState.SignedOut) {
                    _state.value = SessionState.SignedOut
                }
            }
        }
    }

    /** Confirma el token guardado y sincroniza el rol con el servidor (no se confia en el almacenado). */
    fun verify() {
        val stored = store.current ?: run {
            _state.value = SessionState.SignedOut
            return
        }
        _state.value = SessionState.Checking
        viewModelScope.launch {
            try {
                val user = api.me()
                store.save(stored.copy(userId = user.userId, email = user.email, role = user.role, restaurantId = user.restaurantId))
                _state.value = SessionState.SignedIn(user)
            } catch (e: Exception) {
                // Un 401 ya borro la sesion en el interceptor: el colector pasa a SignedOut.
                if (store.current != null) _state.value = SessionState.Error(userMessage(e))
            }
        }
    }

    /** Devuelve null si el login fue correcto, o el mensaje de error para el formulario. */
    suspend fun login(email: String, password: String): String? = try {
        val response = api.login(LoginRequest(email.trim().lowercase(Locale.ROOT), password))
        store.save(
            StoredSession(
                token = response.token,
                userId = response.userId,
                email = response.email,
                role = response.role,
                restaurantId = response.restaurantId
            )
        )
        _state.value = SessionState.SignedIn(
            SessionUser(response.userId, response.email, response.role, response.restaurantId)
        )
        null
    } catch (e: HttpException) {
        // En el login, 401 significa credenciales invalidas (no "sesion expirada").
        when (e.code()) {
            401 -> "Correo o contraseña incorrectos."
            429 -> "Demasiados intentos. Espera un minuto e inténtalo de nuevo."
            else -> userMessage(e)
        }
    } catch (e: Exception) {
        userMessage(e)
    }

    fun logout() {
        store.clear()
        _state.value = SessionState.SignedOut
    }
}
