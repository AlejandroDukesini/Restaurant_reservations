package com.restaurant.reservations.mobile.ui.reservations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restaurant.reservations.mobile.data.ApiService
import com.restaurant.reservations.mobile.data.ReservationDto
import com.restaurant.reservations.mobile.data.TableDto
import com.restaurant.reservations.mobile.data.userMessage
import com.restaurant.reservations.mobile.ui.common.Loadable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Reservas ordenadas por fecha + mesas por id (la reserva solo trae tableId). */
data class ReservationsData(val reservations: List<ReservationDto>, val tables: Map<Long, TableDto>)

data class ReservationsUiState(
    val data: Loadable<ReservationsData> = Loadable.Loading,
    /** null = todas; si no, el estado a mostrar (PENDING, CONFIRMED, CANCELLED). */
    val filter: String? = null,
    val busyId: Long? = null,
    val message: String? = null
)

class ReservationsViewModel(private val api: ApiService) : ViewModel() {

    private val _state = MutableStateFlow(ReservationsUiState())
    val state: StateFlow<ReservationsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (_state.value.data !is Loadable.Ready) _state.update { it.copy(data = Loadable.Loading) }
        viewModelScope.launch {
            try {
                val data = coroutineScope {
                    val reservations = async { api.restaurantReservations() }
                    val tables = async { api.tables() }
                    // ISO local sin zona: el orden lexicografico coincide con el cronologico.
                    ReservationsData(
                        reservations.await().sortedBy { it.reservationDate },
                        tables.await().associateBy { it.id }
                    )
                }
                _state.update { it.copy(data = Loadable.Ready(data), message = null) }
            } catch (e: Exception) {
                val message = userMessage(e)
                _state.update {
                    if (it.data is Loadable.Ready) it.copy(message = message) else it.copy(data = Loadable.Failed(message))
                }
            }
        }
    }

    fun setFilter(status: String?) {
        _state.update { it.copy(filter = status) }
    }

    fun confirm(id: Long) = runAction(id) { api.confirmReservation(id) }

    fun cancel(id: Long) = runAction(id) { api.cancelReservation(id) }

    // Quien puede confirmar/cancelar lo decide el servidor; aqui solo se recarga la lista.
    private fun runAction(id: Long, action: suspend () -> Unit) {
        if (_state.value.busyId != null) return
        _state.update { it.copy(busyId = id) }
        viewModelScope.launch {
            try {
                action()
                load()
            } catch (e: Exception) {
                _state.update { it.copy(message = userMessage(e)) }
            } finally {
                _state.update { it.copy(busyId = null) }
            }
        }
    }
}
