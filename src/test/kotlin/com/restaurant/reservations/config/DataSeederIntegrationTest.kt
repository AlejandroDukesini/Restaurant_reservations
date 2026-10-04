package com.restaurant.reservations.config

import com.restaurant.reservations.model.Role
import com.restaurant.reservations.repository.MenuItemRepository
import com.restaurant.reservations.repository.RestaurantRepository
import com.restaurant.reservations.repository.TableRepository
import com.restaurant.reservations.repository.UserRepository
import com.restaurant.reservations.repository.ZoneRepository
import com.restaurant.reservations.support.IntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.security.crypto.password.PasswordEncoder

/** La siembra de demostracion: contenido esperado, idempotencia e interruptor SEED_ENABLED. */
class DataSeederIntegrationTest : IntegrationTest() {

    @Autowired private lateinit var restaurantRepository: RestaurantRepository
    @Autowired private lateinit var zoneRepository: ZoneRepository
    @Autowired private lateinit var tableRepository: TableRepository
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var menuItemRepository: MenuItemRepository
    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    private val seedPassword = "semilla-sintetica-2026"

    private fun seeder(enabled: Boolean) = DataSeeder(
        restaurantRepository, zoneRepository, tableRepository, userRepository, menuItemRepository,
        passwordEncoder, enabled, seedPassword
    )

    @Test
    fun `siembra un restaurante completo y es idempotente`() {
        val s = seeder(enabled = true)
        s.run(DefaultApplicationArguments())
        s.run(DefaultApplicationArguments()) // segunda ejecucion: no duplica

        assertEquals(1, restaurantRepository.count())
        assertEquals(5, zoneRepository.count())
        assertEquals(16, tableRepository.count())
        assertEquals(10, menuItemRepository.count())

        val users = userRepository.findAll()
        assertEquals(5, users.size)
        assertEquals(mapOf(Role.ADMIN to 1, Role.EMPLOYEE to 2, Role.COOK to 2), users.groupingBy { it.role }.eachCount())
        // La contrasena configurada (SEED_PASSWORD) es la que queda, nunca en claro.
        assertTrue(users.all { passwordEncoder.matches(seedPassword, it.password) })

        val capacities = tableRepository.findAll().associate { it.tableNumber to it.price }
        assertEquals(45.0, capacities[1]) // 2 plazas
        assertEquals(65.0, capacities[3]) // 4 plazas
        assertEquals(95.0, capacities[9]) // 8 plazas
    }

    @Test
    fun `con SEED_ENABLED=false no crea nada`() {
        seeder(enabled = false).run(DefaultApplicationArguments())
        assertEquals(0, restaurantRepository.count())
        assertEquals(0, userRepository.count())
    }
}
