package com.restaurant.reservations.mobile.ui.floor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restaurant.reservations.mobile.data.ApiService
import com.restaurant.reservations.mobile.data.MenuItemDto
import com.restaurant.reservations.mobile.data.OrderDto
import com.restaurant.reservations.mobile.data.OrderRequest
import com.restaurant.reservations.mobile.data.TableDto
import com.restaurant.reservations.mobile.data.requireSuccess
import com.restaurant.reservations.mobile.data.userMessage
import com.restaurant.reservations.mobile.domain.OrderDraft
import com.restaurant.reservations.mobile.ui.common.Loadable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FloorUiState(
    val tables: Loadable<List<TableDto>> = Loadable.Loading,
    // El menu tiene su propia carga: nunca se muestra vacio mientras llega.
    val menu: Loadable<List<MenuItemDto>> = Loadable.Loading,
    val selectedTableId: Long? = null,
    val orders: Loadable<List<OrderDto>> = Loadable.Ready(emptyList()),
    val draft: OrderDraft = OrderDraft(),
    val sending: Boolean = false,
    val message: String? = null
) {
    val selectedTable: TableDto?
        get() = (tables as? Loadable.Ready)?.data?.firstOrNull { it.id == selectedTableId }
}

class FloorViewModel(private val api: ApiService) : ViewModel() {

    private val _state = MutableStateFlow(FloorUiState())
    val state: StateFlow<FloorUiState> = _state.asStateFlow()

    init {
        loadTables()
        loadMenu()
    }

    fun loadTables() {
        _state.update { it.copy(tables = Loadable.Loading) }
        viewModelScope.launch {
            val result = try {
                Loadable.Ready(api.tables())
            } catch (e: Exception) {
                Loadable.Failed(userMessage(e))
            }
            _state.update { it.copy(tables = result) }
        }
    }

    fun loadMenu() {
        _state.update { it.copy(menu = Loadable.Loading) }
        viewModelScope.launch {
            val result = try {
                Loadable.Ready(api.menu())
            } catch (e: Exception) {
                Loadable.Failed(userMessage(e))
            }
            _state.update { it.copy(menu = result) }
        }
    }

    fun selectTable(tableId: Long?) {
        // Cambiar de mesa descarta el borrador, como en el SPA.
        _state.update { it.copy(selectedTableId = tableId, draft = OrderDraft(), message = null) }
        if (tableId != null) loadOrders(tableId)
    }

    /** Recarga los pedidos de [requestedTableId] o, si es null, de la mesa seleccionada. */
    fun loadOrders(requestedTableId: Long? = null) {
        val tableId = requestedTableId ?: _state.value.selectedTableId ?: return
        _state.update { it.copy(orders = Loadable.Loading) }
        viewModelScope.launch {
            val result = try {
                Loadable.Ready(api.ordersByTable(tableId))
            } catch (e: Exception) {
                Loadable.Failed(userMessage(e))
            }
            // Si mientras tanto se eligio otra mesa, la respuesta ya no corresponde.
            _state.update { if (it.selectedTableId == tableId) it.copy(orders = result) else it }
        }
    }

    fun changeQuantity(menuItemId: Long, delta: Int) {
        _state.update { it.copy(draft = it.draft.change(menuItemId, delta)) }
    }

    fun submitOrder() {
        val current = _state.value
        val tableId = current.selectedTableId ?: return
        if (current.draft.isEmpty() || current.sending) return
        _state.update { it.copy(sending = true, message = "Enviando pedido a cocina...") }
        viewModelScope.launch {
            try {
                api.createOrder(OrderRequest(tableId = tableId, items = current.draft.toRequestItems()))
                _state.update { it.copy(sending = false, draft = OrderDraft(), message = "Pedido enviado a cocina.") }
                loadOrders(tableId)
            } catch (e: Exception) {
                _state.update { it.copy(sending = false, message = userMessage(e)) }
            }
        }
    }

    fun deleteOrder(orderId: Long) {
        viewModelScope.launch {
            try {
                api.deleteOrder(orderId).requireSuccess()
                loadOrders()
            } catch (e: Exception) {
                _state.update { it.copy(message = userMessage(e)) }
            }
        }
    }
}
