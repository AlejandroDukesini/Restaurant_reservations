package com.restaurant.reservations.mobile

import android.app.Application
import com.restaurant.reservations.mobile.data.ApiClient
import com.restaurant.reservations.mobile.data.ApiService
import com.restaurant.reservations.mobile.data.SessionStore

/** Dependencias compartidas de la app (inyeccion manual: son solo dos objetos). */
class ReservasApplication : Application() {

    lateinit var sessionStore: SessionStore
        private set
    lateinit var api: ApiService
        private set

    override fun onCreate() {
        super.onCreate()
        sessionStore = SessionStore(this)
        api = ApiClient.create(BuildConfig.API_BASE_URL, sessionStore)
    }
}
