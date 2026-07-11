package com.restaurant.reservations.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero

data class MenuItemRequest(
    @field:NotBlank
    val name: String,

    val description: String? = null,

    // STARTER | MAIN | DESSERT | DRINK
    val category: String = "MAIN",

    @field:PositiveOrZero
    val price: Double,

    val protein: String? = null,
    val condiments: String? = null,
    val ingredients: String? = null,
    val preparationNotes: String? = null
)

data class MenuItemResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val category: String,
    val price: Double,
    val protein: String?,
    val condiments: String?,
    val ingredients: String?,
    val preparationNotes: String?,
    val active: Boolean
)
