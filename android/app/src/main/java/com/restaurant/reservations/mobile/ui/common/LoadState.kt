package com.restaurant.reservations.mobile.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Estado de una carga remota: equivale al patron "null = aun sin primera carga" del SPA. */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Failed(val message: String) : Loadable<Nothing>
    data class Ready<T>(val data: T) : Loadable<T>
}

/** Indicador de carga (equivale a <Loader /> del SPA). */
@Composable
fun Loader(label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator()
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Error de carga con reintento (equivale a <ErrorState /> del SPA). */
@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        OutlinedButton(onClick = onRetry) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Text("Reintentar", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Text(
        message,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

/** Muestra carga / error / contenido segun [state]. */
@Composable
fun <T> LoadableContent(
    state: Loadable<T>,
    loadingLabel: String,
    onRetry: () -> Unit,
    content: @Composable (T) -> Unit
) {
    when (state) {
        Loadable.Loading -> Loader(loadingLabel)
        is Loadable.Failed -> ErrorState(state.message, onRetry)
        is Loadable.Ready -> content(state.data)
    }
}
