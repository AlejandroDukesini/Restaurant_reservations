package com.restaurant.reservations.mobile.ui.kitchen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restaurant.reservations.mobile.data.ApiService
import com.restaurant.reservations.mobile.data.CookQueueItemDto
import com.restaurant.reservations.mobile.data.userMessage
import com.restaurant.reservations.mobile.ui.common.Loadable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class KitchenUiState(
    val queue: Loadable<List<CookQueueItemDto>> = Loadable.Loading,
    val refreshing: Boolean = false,
    /** Error de una accion o de un refresco cuando ya hay una cola en pantalla. */
    val message: String? = null
)

class KitchenViewModel(private val api: ApiService) : ViewModel() {

    private val _state = MutableStateFlow(KitchenUiState())
    val state: StateFlow<KitchenUiState> = _state.asStateFlow()

    init {
        load()
    }

    /** Si ya hay cola en pantalla, un fallo no la borra: se muestra el mensaje encima. */
    fun load() {
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            try {
                val queue = api.kitchenQueue()
                _state.update { it.copy(queue = Loadable.Ready(queue), refreshing = false, message = null) }
            } catch (e: Exception) {
                val message = userMessage(e)
                _state.update {
                    if (it.queue is Loadable.Ready) it.copy(refreshing = false, message = message)
                    else it.copy(queue = Loadable.Failed(message), refreshing = false)
                }
            }
        }
    }

    fun setItemStatus(orderItemId: Long, status: String) {
        viewModelScope.launch {
            try {
                api.updateItemStatus(orderItemId, status)
                load()
            } catch (e: Exception) {
                _state.update { it.copy(message = userMessage(e)) }
            }
        }
    }
}
