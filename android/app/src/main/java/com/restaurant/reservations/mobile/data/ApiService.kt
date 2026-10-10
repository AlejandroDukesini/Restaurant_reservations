package com.restaurant.reservations.mobile.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Endpoints del backend que usa la app. Son los mismos que usa el SPA web; los permisos
 * por rol y el aislamiento por restaurante los aplica siempre el servidor.
 */
interface ApiService {

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("api/auth/me")
    suspend fun me(): SessionUser

    // --- Mesero (EMPLOYEE / ADMIN) ---

    @GET("api/staff/tables")
    suspend fun tables(): List<TableDto>

    @GET("api/staff/menu")
    suspend fun menu(): List<MenuItemDto>

    @GET("api/employee/tables/{tableId}/orders")
    suspend fun ordersByTable(@Path("tableId") tableId: Long): List<OrderDto>

    @POST("api/employee/orders")
    suspend fun createOrder(@Body request: OrderRequest): OrderDto

    // 204 sin cuerpo: Response<Unit> evita depender del convertidor para un cuerpo vacio.
    @DELETE("api/employee/orders/{id}")
    suspend fun deleteOrder(@Path("id") id: Long): Response<Unit>

    // --- Cocina (COOK / ADMIN) ---

    @GET("api/cook/queue")
    suspend fun kitchenQueue(): List<CookQueueItemDto>

    @PUT("api/cook/order-items/{id}/status")
    suspend fun updateItemStatus(@Path("id") orderItemId: Long, @Query("status") status: String): CookQueueItemDto

    // --- Reservas (ADMIN) ---

    @GET("api/admin/reservations")
    suspend fun restaurantReservations(): List<ReservationDto>

    @PUT("api/customer/reservations/{id}/confirm")
    suspend fun confirmReservation(@Path("id") id: Long): ReservationDto

    @PUT("api/customer/reservations/{id}/cancel")
    suspend fun cancelReservation(@Path("id") id: Long): ReservationDto
}
