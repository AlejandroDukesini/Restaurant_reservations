package com.restaurant.reservations.support

import com.restaurant.reservations.model.MenuCategory
import com.restaurant.reservations.model.MenuItem
import com.restaurant.reservations.model.Restaurant
import com.restaurant.reservations.model.RestaurantTable
import com.restaurant.reservations.model.Role
import com.restaurant.reservations.model.TableStatus
import com.restaurant.reservations.model.User
import com.restaurant.reservations.model.Zone
import com.restaurant.reservations.repository.MenuItemRepository
import com.restaurant.reservations.repository.RestaurantRepository
import com.restaurant.reservations.repository.TableRepository
import com.restaurant.reservations.repository.UserRepository
import com.restaurant.reservations.repository.ZoneRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fabrica de datos sinteticos para las pruebas de integracion. Todos los correos
 * usan el dominio reservado `example.test` y ningun dato corresponde a personas reales.
 */
@Component
class TestData(
    private val restaurantRepository: RestaurantRepository,
    private val zoneRepository: ZoneRepository,
    private val tableRepository: TableRepository,
    private val userRepository: UserRepository,
    private val menuItemRepository: MenuItemRepository,
    private val passwordEncoder: PasswordEncoder
) {
    private val sequence = AtomicInteger()

    // BCrypt con coste 12 tarda cientos de ms: se calcula una sola vez por contexto.
    val passwordHash: String by lazy { passwordEncoder.encode(PASSWORD) }

    fun restaurant(name: String = "Restaurante ${sequence.incrementAndGet()}"): Restaurant {
        val slug = name.lowercase().replace(" ", "-").replace("[^a-z0-9-]".toRegex(), "")
        return restaurantRepository.save(
            Restaurant(
                name = name,
                slug = slug,
                description = "Restaurante de prueba",
                address = "Calle Falsa 123",
                phone = "+00 000 000",
                email = "contacto@$slug.example.test",
                numberOfTables = 4,
                numberOfChairs = 16,
                numberOfFloors = 1,
                websiteUrl = "/$slug"
            )
        )
    }

    fun user(
        role: Role,
        restaurant: Restaurant?,
        email: String = "${role.name.lowercase()}${sequence.incrementAndGet()}@example.test",
        active: Boolean = true
    ): User = userRepository.save(
        User(
            email = email,
            password = passwordHash,
            name = "Usuario ${role.name}",
            role = role,
            restaurant = restaurant,
            active = active
        )
    )

    fun zone(restaurant: Restaurant, code: String = "CENTRAL", sortOrder: Int = 1): Zone =
        zoneRepository.save(Zone(name = "Zona $code", code = code, sortOrder = sortOrder, restaurant = restaurant))

    fun table(
        restaurant: Restaurant,
        tableNumber: Int = sequence.incrementAndGet(),
        capacity: Int = 4,
        zone: Zone? = null,
        status: TableStatus = TableStatus.AVAILABLE,
        active: Boolean = true
    ): RestaurantTable = tableRepository.save(
        RestaurantTable(
            tableNumber = tableNumber,
            floor = 1,
            capacity = capacity,
            price = 50.0,
            zone = zone,
            gridX = 1,
            gridY = 1,
            status = status,
            restaurant = restaurant,
            active = active
        )
    )

    fun menuItem(
        restaurant: Restaurant,
        name: String = "Plato ${sequence.incrementAndGet()}",
        price: Double = 10.0,
        category: MenuCategory = MenuCategory.MAIN
    ): MenuItem = menuItemRepository.save(
        MenuItem(
            name = name,
            category = category,
            price = price,
            protein = "Pollo",
            condiments = "Sal",
            ingredients = "Ingrediente de prueba",
            preparationNotes = "Cocinar 10 min",
            restaurant = restaurant
        )
    )

    companion object {
        /** Contrasena sintetica de las cuentas de prueba (cumple el minimo de 12). */
        const val PASSWORD = "Sintetica-Prueba-2026"
    }
}
