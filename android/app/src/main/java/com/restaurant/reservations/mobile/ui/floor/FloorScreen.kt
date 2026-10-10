package com.restaurant.reservations.mobile.ui.floor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.restaurant.reservations.mobile.data.MenuItemDto
import com.restaurant.reservations.mobile.data.OrderDto
import com.restaurant.reservations.mobile.data.TableDto
import com.restaurant.reservations.mobile.domain.CATEGORY_LABEL
import com.restaurant.reservations.mobile.domain.ITEM_STATUS_LABEL
import com.restaurant.reservations.mobile.domain.ORDER_STATUS_LABEL
import com.restaurant.reservations.mobile.domain.money
import com.restaurant.reservations.mobile.ui.common.EmptyState
import com.restaurant.reservations.mobile.ui.common.ErrorState
import com.restaurant.reservations.mobile.ui.common.Loadable
import com.restaurant.reservations.mobile.ui.common.LoadableContent
import com.restaurant.reservations.mobile.ui.common.Loader

@Composable
fun FloorScreen(viewModel: FloorViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selected = state.selectedTable

    if (selected == null) {
        LoadableContent(state.tables, "Cargando el mapa de mesas...", viewModel::loadTables) { tables ->
            TableList(tables, onSelect = { viewModel.selectTable(it.id) })
        }
    } else {
        BackHandler { viewModel.selectTable(null) }
        TableDetail(
            table = selected,
            state = state,
            onBack = { viewModel.selectTable(null) },
            onRetryMenu = viewModel::loadMenu,
            onRetryOrders = { viewModel.loadOrders() },
            onChange = viewModel::changeQuantity,
            onSubmit = viewModel::submitOrder,
            onDeleteOrder = viewModel::deleteOrder
        )
    }
}

private fun tableTitle(table: TableDto) = table.name ?: "Mesa ${table.tableNumber}"

@Composable
private fun TableList(tables: List<TableDto>, onSelect: (TableDto) -> Unit) {
    if (tables.isEmpty()) {
        EmptyState("No hay mesas activas.")
        return
    }
    // Agrupadas por zona, en el orden en que llegan (igual que FloorMap en el SPA).
    val byZone = tables.groupBy { it.zoneName ?: "Zona Central" }
    LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
        item {
            Text(
                "Selecciona una mesa para tomar o revisar un pedido.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }
        byZone.forEach { (zone, zoneTables) ->
            item(key = "zone-$zone") {
                Text(
                    zone,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }
            items(zoneTables, key = { it.id }) { table ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable(role = Role.Button) { onSelect(table) }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tableTitle(table), fontWeight = FontWeight.SemiBold)
                            Text(
                                "#${table.tableNumber} · ${table.capacity} personas",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            if (table.status == "AVAILABLE") "Disponible" else "No disponible",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (table.status == "AVAILABLE") MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TableDetail(
    table: TableDto,
    state: FloorUiState,
    onBack: () -> Unit,
    onRetryMenu: () -> Unit,
    onRetryOrders: () -> Unit,
    onChange: (Long, Int) -> Unit,
    onSubmit: () -> Unit,
    onDeleteOrder: (Long) -> Unit
) {
    LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver al mapa")
                }
                Column {
                    Text(tableTitle(table), style = MaterialTheme.typography.titleLarge)
                    Text(
                        "#${table.tableNumber} · ${table.capacity} personas · ${table.zoneName ?: "Zona Central"}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            state.message?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            }
            Text("Nuevo pedido", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
        }

        when (val menu = state.menu) {
            Loadable.Loading -> item { Loader("Cargando el menú...") }
            is Loadable.Failed -> item { ErrorState(menu.message, onRetryMenu) }
            is Loadable.Ready -> {
                if (menu.data.isEmpty()) {
                    item { EmptyState("No hay platos activos en el menú.") }
                }
                items(menu.data, key = { "menu-${it.id}" }) { item ->
                    MenuRow(item, state.draft.quantityOf(item.id), onChange)
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Text("Total", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(money(state.draft.total(menu.data)), fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onSubmit,
                        enabled = !state.draft.isEmpty() && !state.sending,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Enviar a cocina")
                    }
                }
            }
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Text("Pedidos de la mesa", style = MaterialTheme.typography.titleMedium)
        }
        when (val orders = state.orders) {
            Loadable.Loading -> item { Loader("Cargando pedidos...") }
            is Loadable.Failed -> item { ErrorState(orders.message, onRetryOrders) }
            is Loadable.Ready -> {
                if (orders.data.isEmpty()) item { EmptyState("Sin pedidos aún.") }
                items(orders.data, key = { "order-${it.id}" }) { order -> OrderCard(order, onDeleteOrder) }
            }
        }
        item { Spacer(Modifier.padding(bottom = 24.dp)) }
    }
}

@Composable
private fun MenuRow(item: MenuItemDto, quantity: Int, onChange: (Long, Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name)
            Text(
                "${CATEGORY_LABEL[item.category] ?: item.category} · ${money(item.price)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        OutlinedButton(
            onClick = { onChange(item.id, -1) },
            enabled = quantity > 0,
            modifier = Modifier.semantics { contentDescription = "Quitar uno de ${item.name}" }
        ) { Text("−") }
        Text("$quantity", modifier = Modifier.padding(horizontal = 12.dp))
        OutlinedButton(
            onClick = { onChange(item.id, 1) },
            modifier = Modifier.semantics { contentDescription = "Agregar uno de ${item.name}" }
        ) { Text("+") }
    }
}

@Composable
private fun OrderCard(order: OrderDto, onDelete: (Long) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ORDER_STATUS_LABEL[order.status] ?: order.status,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Text(money(order.totalAmount))
                IconButton(onClick = { onDelete(order.id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Eliminar pedido")
                }
            }
            order.items.forEach { item ->
                Row {
                    Text("${item.quantity}× ${item.itemName}", modifier = Modifier.weight(1f))
                    Text(
                        ITEM_STATUS_LABEL[item.status] ?: item.status,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
