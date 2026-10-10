@file:OptIn(ExperimentalMaterial3Api::class)

package com.restaurant.reservations.mobile.ui.reservations

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.restaurant.reservations.mobile.data.ReservationDto
import com.restaurant.reservations.mobile.data.TableDto
import com.restaurant.reservations.mobile.domain.RESERVATION_STATUS_LABEL
import com.restaurant.reservations.mobile.domain.canCancelReservation
import com.restaurant.reservations.mobile.domain.canConfirmReservation
import com.restaurant.reservations.mobile.domain.formatDateTime
import com.restaurant.reservations.mobile.ui.common.EmptyState
import com.restaurant.reservations.mobile.ui.common.LoadableContent

private val FILTERS = listOf(null to "Todas", "PENDING" to "Pendientes", "CONFIRMED" to "Confirmadas", "CANCELLED" to "Canceladas")

@Composable
fun ReservationsScreen(viewModel: ReservationsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            // El titulo "Reservas" ya esta en la barra superior.
            Text(
                "Reservas de las mesas, por fecha",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).align(Alignment.CenterVertically)
            )
            OutlinedButton(onClick = viewModel::load) { Text("Actualizar") }
        }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        LoadableContent(state.data, "Cargando reservas...", viewModel::load) { data ->
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FILTERS.forEach { (status, label) ->
                    FilterChip(
                        selected = state.filter == status,
                        onClick = { viewModel.setFilter(status) },
                        label = { Text(label) }
                    )
                }
            }
            val visible = data.reservations.filter { state.filter == null || it.status == state.filter }
            if (visible.isEmpty()) {
                EmptyState(if (data.reservations.isEmpty()) "Sin reservas." else "Sin reservas con este estado.")
            } else {
                LazyColumn {
                    items(visible, key = { it.id }) { reservation ->
                        ReservationCard(
                            reservation = reservation,
                            table = data.tables[reservation.tableId],
                            busy = state.busyId == reservation.id,
                            onConfirm = { viewModel.confirm(reservation.id) },
                            onCancel = { viewModel.cancel(reservation.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReservationCard(
    reservation: ReservationDto,
    table: TableDto?,
    busy: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text(
                    table?.let { it.name ?: "Mesa ${it.tableNumber}" } ?: "Mesa",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(RESERVATION_STATUS_LABEL[reservation.status] ?: reservation.status, color = MaterialTheme.colorScheme.primary)
            }
            Text(formatDateTime(reservation.reservationDate), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${reservation.customerName} · ${reservation.numberOfGuests} personas")
            reservation.specialRequests?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canConfirmReservation(reservation.status)) {
                    OutlinedButton(onClick = onConfirm, enabled = !busy) { Text("Confirmar") }
                }
                if (canCancelReservation(reservation.status)) {
                    TextButton(onClick = onCancel, enabled = !busy) { Text("Cancelar") }
                }
            }
        }
    }
}
