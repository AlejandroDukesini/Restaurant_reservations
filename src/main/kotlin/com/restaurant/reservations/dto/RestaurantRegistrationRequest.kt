package com.restaurant.reservations.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Alta de restaurante. Crea tambien la cuenta ADMIN del nuevo tenant, por lo que
 * la contrasena del administrador tiene los mismos minimos que el registro publico.
 * Los limites de longitud y de cantidad evitan que un cliente autenticado agote
 * memoria o almacenamiento con valores desproporcionados.
 */
data class RestaurantRegistrationRequest(
    @field:NotBlank
    @field:Size(max = 120)
    val name: String,

    @field:Size(max = 2000)
    val description: String,

    @field:Size(max = 300)
    val address: String,

    @field:Size(max = 40)
    val phone: String,

    @field:Email
    @field:Size(max = 254)
    val email: String,

    @field:Min(0)
    @field:Max(10_000)
    val numberOfTables: Int,

    @field:Min(0)
    @field:Max(100_000)
    val numberOfChairs: Int,

    @field:Min(1)
    @field:Max(100)
    val numberOfFloors: Int,

    @field:NotBlank
    @field:Email
    @field:Size(max = 254)
    val adminEmail: String,

    @field:NotBlank
    @field:Size(min = 12, max = 128, message = "La contrasena debe tener entre 12 y 128 caracteres")
    val adminPassword: String,

    @field:NotBlank
    @field:Size(max = 120)
    val adminName: String
)
