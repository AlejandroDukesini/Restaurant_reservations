package com.restaurant.reservations.config

import com.restaurant.reservations.model.MenuCategory
import com.restaurant.reservations.model.MenuItem
import com.restaurant.reservations.model.Restaurant
import com.restaurant.reservations.model.RestaurantTable
import com.restaurant.reservations.model.Role
import com.restaurant.reservations.model.User
import com.restaurant.reservations.model.Zone
import com.restaurant.reservations.repository.MenuItemRepository
import com.restaurant.reservations.repository.RestaurantRepository
import com.restaurant.reservations.repository.TableRepository
import com.restaurant.reservations.repository.UserRepository
import com.restaurant.reservations.repository.ZoneRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class DataSeeder(
    private val restaurantRepository: RestaurantRepository,
    private val zoneRepository: ZoneRepository,
    private val tableRepository: TableRepository,
    private val userRepository: UserRepository,
    private val menuItemRepository: MenuItemRepository,
    private val passwordEncoder: PasswordEncoder
) : ApplicationRunner {

    @Transactional
    override fun run(args: ApplicationArguments) {
        if (restaurantRepository.count() > 0) return

        val restaurant = restaurantRepository.save(
            Restaurant(
                name = "Maison Noir",
                slug = "maison-noir",
                description = "Experiencia gastronómica premium multi-tenant",
                address = "Av. Premium 1200, Ciudad",
                phone = "+57 300 000 0000",
                email = "reservas@maisonnoir.com",
                numberOfTables = 16,
                numberOfChairs = 56,
                numberOfFloors = 1,
                websiteUrl = "https://maisonnoir.com"
            )
        )

        seedUsers(restaurant)
        seedMenu(restaurant)

        val zoneSpecs = listOf(
            Triple("Vistas a la Ventana", "WINDOW", 1),
            Triple("Cerca de la Cocina", "KITCHEN", 2),
            Triple("Zona Central", "CENTRAL", 3),
            Triple("Barra de Cócteles", "BAR", 4),
            Triple("Terraza Exterior", "TERRACE", 5)
        )

        val zones = zoneSpecs.map { (name, code, order) ->
            zoneRepository.save(
                Zone(
                    name = name,
                    code = code,
                    sortOrder = order,
                    restaurant = restaurant
                )
            )
        }

        val tableSpecs = listOf(
            TableSeed(1, 2, 1, 1, 0, "Ventana 1"),
            TableSeed(2, 2, 2, 1, 0, "Ventana 2"),
            TableSeed(3, 4, 3, 1, 0, "Ventana 3"),
            TableSeed(4, 4, 1, 1, 1),
            TableSeed(5, 6, 2, 1, 1),
            TableSeed(6, 2, 3, 1, 1),
            TableSeed(7, 4, 1, 1, 2),
            TableSeed(8, 4, 2, 1, 2),
            TableSeed(9, 8, 3, 1, 2, "Mesa Familiar"),
            TableSeed(10, 6, 2, 2, 2, "Mesa VIP"),
            TableSeed(11, 2, 1, 1, 3, "Barra 1"),
            TableSeed(12, 2, 2, 1, 3, "Barra 2"),
            TableSeed(13, 2, 3, 1, 3, "Barra 3"),
            TableSeed(14, 4, 1, 1, 4),
            TableSeed(15, 6, 2, 1, 4),
            TableSeed(16, 2, 3, 1, 4)
        )

        tableSpecs.forEach { spec ->
            tableRepository.save(
                RestaurantTable(
                    tableNumber = spec.tableNumber,
                    name = spec.name,
                    floor = 1,
                    capacity = spec.capacity,
                    price = when {
                        spec.capacity <= 2 -> 45.0
                        spec.capacity <= 4 -> 65.0
                        else -> 95.0
                    },
                    zone = zones[spec.zoneIndex],
                    gridX = spec.gridX,
                    gridY = spec.gridY,
                    restaurant = restaurant
                )
            )
        }
    }

    private fun seedUsers(restaurant: Restaurant) {
        val users = listOf(
            UserSeed("admin@maisonnoir.com", "Admin Maison", Role.ADMIN),
            UserSeed("mesero1@maisonnoir.com", "Carlos Mesero", Role.EMPLOYEE),
            UserSeed("mesero2@maisonnoir.com", "Lucía Mesera", Role.EMPLOYEE),
            UserSeed("cocina1@maisonnoir.com", "Chef Alejo", Role.COOK),
            UserSeed("cocina2@maisonnoir.com", "Chef Marta", Role.COOK)
        )
        users.forEach { seed ->
            userRepository.save(
                User(
                    email = seed.email,
                    // Contraseña de demo para todos: "password123"
                    password = passwordEncoder.encode("password123"),
                    name = seed.name,
                    role = seed.role,
                    restaurant = restaurant
                )
            )
        }
    }

    private fun seedMenu(restaurant: Restaurant) {
        val items = listOf(
            MenuSeed(
                "Bruschetta Trufada", MenuCategory.STARTER, 18.0,
                protein = "Ninguna",
                condiments = "Aceite de oliva, sal Maldon, pimienta",
                ingredients = "Pan de masa madre, tomate cherry, aceite de trufa, albahaca",
                prep = "Tostar el pan, montar en frío, servir de inmediato"
            ),
            MenuSeed(
                "Carpaccio de Res", MenuCategory.STARTER, 24.0,
                protein = "Res (lomo fino)",
                condiments = "Limón, alcaparras, parmesano, aceite de oliva",
                ingredients = "Lomo de res laminado, rúcula, láminas de parmesano",
                prep = "Laminar en frío, aliñar al momento"
            ),
            MenuSeed(
                "Risotto de Hongos", MenuCategory.MAIN, 32.0,
                protein = "Ninguna",
                condiments = "Mantequilla, parmesano, tomillo, sal, pimienta",
                ingredients = "Arroz arborio, hongos portobello, caldo de verduras, vino blanco",
                prep = "Cocción lenta 18 min removiendo, mantecar al final"
            ),
            MenuSeed(
                "Salmón a la Parrilla", MenuCategory.MAIN, 38.0,
                protein = "Salmón",
                condiments = "Eneldo, limón, sal, pimienta, mantequilla",
                ingredients = "Filete de salmón, espárragos, puré de papa",
                prep = "Parrilla 4 min por lado, piel crujiente"
            ),
            MenuSeed(
                "Filete Mignon", MenuCategory.MAIN, 46.0,
                protein = "Res (filete mignon)",
                condiments = "Sal, pimienta, romero, mantequilla, salsa de vino tinto",
                ingredients = "Medallón de res, papas confitadas, reducción de vino",
                prep = "Sellar y terminar al horno; preguntar término de cocción"
            ),
            MenuSeed(
                "Pollo al Curry", MenuCategory.MAIN, 30.0,
                protein = "Pollo",
                condiments = "Curry, comino, cúrcuma, jengibre, leche de coco",
                ingredients = "Pechuga de pollo, arroz basmati, cilantro",
                prep = "Guisar 20 min, servir con arroz aparte"
            ),
            MenuSeed(
                "Tiramisú", MenuCategory.DESSERT, 14.0,
                protein = "Ninguna",
                condiments = "Cacao, café espresso",
                ingredients = "Mascarpone, bizcochos savoiardi, café, huevo",
                prep = "Montar en capas, reposar en frío mínimo 4h"
            ),
            MenuSeed(
                "Coulant de Chocolate", MenuCategory.DESSERT, 16.0,
                protein = "Ninguna",
                condiments = "Azúcar glas",
                ingredients = "Chocolate 70%, mantequilla, huevo, harina, helado de vainilla",
                prep = "Horno 220°C por 9 min, centro líquido; servir caliente"
            ),
            MenuSeed(
                "Copa de Vino Tinto", MenuCategory.DRINK, 12.0,
                protein = null,
                condiments = null,
                ingredients = "Reserva Malbec",
                prep = "Servir a 16-18°C"
            ),
            MenuSeed(
                "Cóctel de la Casa", MenuCategory.DRINK, 15.0,
                protein = null,
                condiments = "Angostura, twist de naranja",
                ingredients = "Gin premium, vermut, licor de naranja",
                prep = "Mezclar con hielo, colar, decorar con twist"
            )
        )
        items.forEach { seed ->
            menuItemRepository.save(
                MenuItem(
                    name = seed.name,
                    category = seed.category,
                    price = seed.price,
                    protein = seed.protein,
                    condiments = seed.condiments,
                    ingredients = seed.ingredients,
                    preparationNotes = seed.prep,
                    restaurant = restaurant
                )
            )
        }
    }

    private data class TableSeed(
        val tableNumber: Int,
        val capacity: Int,
        val gridX: Int,
        val gridY: Int,
        val zoneIndex: Int,
        val name: String? = null
    )

    private data class UserSeed(
        val email: String,
        val name: String,
        val role: Role
    )

    private data class MenuSeed(
        val name: String,
        val category: MenuCategory,
        val price: Double,
        val protein: String?,
        val condiments: String?,
        val ingredients: String?,
        val prep: String?
    )
}
