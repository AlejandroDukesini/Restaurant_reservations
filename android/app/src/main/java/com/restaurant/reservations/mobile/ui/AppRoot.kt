@file:OptIn(ExperimentalMaterial3Api::class)

package com.restaurant.reservations.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.restaurant.reservations.mobile.data.ApiService
import com.restaurant.reservations.mobile.data.SessionUser
import com.restaurant.reservations.mobile.domain.Destination
import com.restaurant.reservations.mobile.domain.destinationsFor
import com.restaurant.reservations.mobile.ui.common.ErrorState
import com.restaurant.reservations.mobile.ui.common.Loader
import com.restaurant.reservations.mobile.ui.floor.FloorScreen
import com.restaurant.reservations.mobile.ui.floor.FloorViewModel
import com.restaurant.reservations.mobile.ui.kitchen.KitchenScreen
import com.restaurant.reservations.mobile.ui.kitchen.KitchenViewModel
import com.restaurant.reservations.mobile.ui.login.LoginScreen
import com.restaurant.reservations.mobile.ui.reservations.ReservationsScreen
import com.restaurant.reservations.mobile.ui.reservations.ReservationsViewModel
import com.restaurant.reservations.mobile.ui.session.SessionState
import com.restaurant.reservations.mobile.ui.session.SessionViewModel

@Composable
fun AppRoot(sessionViewModel: SessionViewModel, api: ApiService) {
    val session by sessionViewModel.state.collectAsStateWithLifecycle()

    when (val s = session) {
        // Sin rol confirmado no se muestra contenido restringido (como SessionGate en el SPA).
        SessionState.Checking -> Centered { Loader("Verificando tu sesión...") }
        is SessionState.Error -> Centered {
            ErrorState(s.message, onRetry = sessionViewModel::verify)
            OutlinedButton(onClick = sessionViewModel::logout) { Text("Cerrar sesión") }
        }
        SessionState.SignedOut -> LoginScreen(onLogin = sessionViewModel::login)
        // key: al cambiar de usuario se descartan los ViewModels y estados del anterior.
        is SessionState.SignedIn -> androidx.compose.runtime.key(s.user.userId) {
            StaffHome(s.user, api, onLogout = sessionViewModel::logout)
        }
    }
}

@Composable
private fun StaffHome(user: SessionUser, api: ApiService, onLogout: () -> Unit) {
    val destinations = destinationsFor(user.role)
    if (destinations.isEmpty()) {
        Centered {
            Text("Esta app es para el personal del restaurante.", modifier = Modifier.padding(16.dp))
            OutlinedButton(onClick = onLogout) { Text("Cerrar sesión") }
        }
        return
    }

    var current by rememberSaveable { mutableStateOf(destinations.first()) }
    if (current !in destinations) current = destinations.first()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(current.label)
                        Text(
                            "${user.email} · ${user.role}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Salir")
                    }
                }
            )
        },
        bottomBar = {
            if (destinations.size > 1) {
                NavigationBar {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = destination == current,
                            onClick = { current = destination },
                            icon = { Icon(iconFor(destination), contentDescription = null) },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            // Clave por usuario: los ViewModel viven en la actividad, y sin ella otra persona que
            // inicie sesion en el mismo telefono veria los datos cargados por la anterior.
            val scope = "user-${user.userId}"
            when (current) {
                Destination.FLOOR ->
                    FloorScreen(viewModel(key = "$scope-floor", factory = factory { FloorViewModel(api) }))
                Destination.KITCHEN ->
                    KitchenScreen(viewModel(key = "$scope-kitchen", factory = factory { KitchenViewModel(api) }))
                Destination.RESERVATIONS ->
                    ReservationsScreen(viewModel(key = "$scope-reservations", factory = factory { ReservationsViewModel(api) }))
            }
        }
    }
}

private fun iconFor(destination: Destination) = when (destination) {
    Destination.FLOOR -> Icons.Filled.Home
    Destination.KITCHEN -> Icons.AutoMirrored.Filled.List
    Destination.RESERVATIONS -> Icons.Filled.DateRange
}

private inline fun <reified VM : androidx.lifecycle.ViewModel> factory(crossinline create: () -> VM) =
    viewModelFactory { initializer { create() } }

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) { content() }
}
