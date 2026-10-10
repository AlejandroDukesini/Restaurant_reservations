package com.restaurant.reservations.mobile.ui.kitchen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.restaurant.reservations.mobile.data.CookQueueItemDto
import com.restaurant.reservations.mobile.domain.CATEGORY_LABEL
import com.restaurant.reservations.mobile.domain.ITEM_STATUS_LABEL
import com.restaurant.reservations.mobile.domain.canMarkPreparing
import com.restaurant.reservations.mobile.ui.common.EmptyState
import com.restaurant.reservations.mobile.ui.common.LoadableContent
import kotlinx.coroutines.delay

private const val AUTO_REFRESH_MS = 15_000L // igual que KitchenPage del SPA

@Composable
fun KitchenScreen(viewModel: KitchenViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Solo mientras la pantalla esta en composicion: al salir se cancela el refresco.
    LaunchedEffect(Unit) {
        while (true) {
            delay(AUTO_REFRESH_MS)
            viewModel.load()
        }
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Platos por preparar", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = viewModel::load, enabled = !state.refreshing) {
                Text(if (state.refreshing) "Actualizando..." else "Actualizar")
            }
        }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        LoadableContent(state.queue, "Cargando la cola de cocina...", viewModel::load) { queue ->
            if (queue.isEmpty()) {
                EmptyState("No hay platos pendientes. La cocina está al día.")
            } else {
                LazyColumn {
                    items(queue, key = { it.orderItemId }) { item ->
                        QueueCard(item, onStatus = { status -> viewModel.setItemStatus(item.orderItemId, status) })
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueCard(item: CookQueueItemDto, onStatus: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text("${item.quantity}× ${item.itemName}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(ITEM_STATUS_LABEL[item.status] ?: item.status, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                "${item.tableName ?: "Mesa ${item.tableNumber}"} · ${item.employeeName}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            RecipeLine("Categoría", item.category?.let { CATEGORY_LABEL[it] ?: it })
            RecipeLine("Proteína", item.protein)
            RecipeLine("Condimentos", item.condiments)
            RecipeLine("Ingredientes", item.ingredients)
            RecipeLine("Preparación", item.preparationNotes)
            RecipeLine("Notas", item.notes)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = { onStatus("PREPARING") }, enabled = canMarkPreparing(item.status)) {
                    Text("Preparando")
                }
                Button(onClick = { onStatus("READY") }) { Text("Listo") }
            }
        }
    }
}

@Composable
private fun RecipeLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Text("$label: $value", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
