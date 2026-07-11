package com.restaurant.reservations.model

import jakarta.persistence.*

@Entity
@Table(name = "menu_items")
data class MenuItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val name: String,

    @Column
    val description: String? = null,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    val category: MenuCategory = MenuCategory.MAIN,

    @Column(nullable = false)
    val price: Double,

    // Especificaciones de receta (fija, no personalizable) que consulta el cocinero
    @Column
    val protein: String? = null,

    @Column(length = 1000)
    val condiments: String? = null,

    @Column(length = 2000)
    val ingredients: String? = null,

    @Column(length = 1000)
    val preparationNotes: String? = null,

    @ManyToOne
    @JoinColumn(name = "restaurant_id", nullable = false)
    val restaurant: Restaurant,

    @Column(nullable = false)
    val active: Boolean = true
)

enum class MenuCategory {
    STARTER,
    MAIN,
    DESSERT,
    DRINK
}
