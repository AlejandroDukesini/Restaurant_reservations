package com.restaurant.reservations.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.restaurant.reservations.mobile.ui.AppRoot
import com.restaurant.reservations.mobile.ui.session.SessionViewModel
import com.restaurant.reservations.mobile.ui.theme.ReservasTheme

class MainActivity : ComponentActivity() {

    private val app: ReservasApplication get() = application as ReservasApplication

    private val sessionViewModel: SessionViewModel by viewModels {
        viewModelFactory { initializer { SessionViewModel(app.api, app.sessionStore) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ReservasTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(sessionViewModel, app.api)
                }
            }
        }
    }
}
