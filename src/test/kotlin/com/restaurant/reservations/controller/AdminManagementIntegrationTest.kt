package com.restaurant.reservations.controller

import com.restaurant.reservations.model.Restaurant
import com.restaurant.reservations.model.Role
import com.restaurant.reservations.model.User
import com.restaurant.reservations.repository.MenuItemRepository
import com.restaurant.reservations.repository.RestaurantRepository
import com.restaurant.reservations.repository.TableRepository
import com.restaurant.reservations.repository.UserRepository
import com.restaurant.reservations.support.IntegrationTest
import com.restaurant.reservations.support.TestData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.file.Files
import java.nio.file.Paths

/** CRUD del panel de administracion: personal, menu, mesas y restaurantes, con aislamiento por restaurante. */
class AdminManagementIntegrationTest : IntegrationTest() {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var menuItemRepository: MenuItemRepository
    @Autowired private lateinit var tableRepository: TableRepository
    @Autowired private lateinit var restaurantRepository: RestaurantRepository

    private lateinit var restaurant: Restaurant
    private lateinit var admin: User
    private lateinit var foreignAdmin: User

    @BeforeEach
    fun setUp() {
        restaurant = data.restaurant()
        admin = data.user(Role.ADMIN, restaurant)
        foreignAdmin = data.user(Role.ADMIN, data.restaurant())
    }

    private fun staffBody(email: String, role: String = "COOK", password: String? = TestData.PASSWORD, active: Boolean = true) =
        mapOf("name" to "Chef Prueba", "email" to email, "password" to password, "role" to role, "active" to active)

    private fun login(email: String, password: String = TestData.PASSWORD) =
        mockMvc.perform(post("/api/auth/login").json(mapOf("email" to email, "password" to password)))

    // --- Personal --------------------------------------------------------------

    @Test
    fun `alta de personal crea la cuenta en el restaurante del admin y puede iniciar sesion`() {
        mockMvc.perform(post("/api/admin/staff").authAs(admin).json(staffBody("chef@example.test")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.role").value("COOK"))
            .andExpect(jsonPath("$.password").doesNotExist())

        val stored = userRepository.findByEmail("chef@example.test").orElseThrow()
        assertEquals(restaurant.id, stored.restaurant?.id)
        login("chef@example.test").andExpect(status().isOk)
    }

    @Test
    fun `el email del personal se normaliza para que el login (que normaliza) lo encuentre`() {
        mockMvc.perform(post("/api/admin/staff").authAs(admin).json(staffBody("Chef.Mayus@Example.TEST")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("chef.mayus@example.test"))

        login("Chef.Mayus@Example.TEST").andExpect(status().isOk)
    }

    @Test
    fun `no se puede crear personal con rol ADMIN ni CUSTOMER`() {
        listOf("ADMIN", "CUSTOMER", "INVENTADO").forEach { role ->
            mockMvc.perform(post("/api/admin/staff").authAs(admin).json(staffBody("x-$role@example.test", role = role)))
                .andExpect(status().isBadRequest)
        }
        assertEquals(2, userRepository.count()) // solo los dos admins del setUp
    }

    @Test
    fun `alta de personal sin contrasena o con email repetido responde 400`() {
        mockMvc.perform(post("/api/admin/staff").authAs(admin).json(staffBody("sin-clave@example.test", password = null)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Password is required"))
        mockMvc.perform(post("/api/admin/staff").authAs(admin).json(staffBody(admin.email.uppercase())))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Email already registered"))
    }

    @Test
    fun `listado de personal solo muestra el del propio restaurante`() {
        data.user(Role.EMPLOYEE, restaurant)
        data.user(Role.COOK, foreignAdmin.restaurant)

        mockMvc.perform(get("/api/admin/staff").authAs(admin))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].role").value("EMPLOYEE"))
    }

    @Test
    fun `editar sin contrasena conserva la anterior y dar de baja impide el login`() {
        val waiter = data.user(Role.EMPLOYEE, restaurant)

        mockMvc.perform(put("/api/admin/staff/${waiter.id}").authAs(admin).json(staffBody(waiter.email, role = "COOK", password = null)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.role").value("COOK"))
        login(waiter.email).andExpect(status().isOk)

        mockMvc.perform(delete("/api/admin/staff/${waiter.id}").authAs(admin)).andExpect(status().isNoContent)
        assertFalse(userRepository.findById(waiter.id!!).orElseThrow().active)
        login(waiter.email).andExpect(status().isUnauthorized)
    }

    @Test
    fun `editar personal con el email de otra cuenta responde 400 y no 500`() {
        val waiter = data.user(Role.EMPLOYEE, restaurant)

        mockMvc.perform(put("/api/admin/staff/${waiter.id}").authAs(admin).json(staffBody(admin.email, role = "EMPLOYEE", password = null)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Email already registered"))
        assertEquals(waiter.email, userRepository.findById(waiter.id!!).orElseThrow().email)
    }

    @Test
    fun `un admin no puede editar ni borrar personal de otro restaurante`() {
        val foreignWaiter = data.user(Role.EMPLOYEE, foreignAdmin.restaurant)

        mockMvc.perform(put("/api/admin/staff/${foreignWaiter.id}").authAs(admin).json(staffBody(foreignWaiter.email, role = "EMPLOYEE")))
            .andExpect(status().isBadRequest)
        mockMvc.perform(delete("/api/admin/staff/${foreignWaiter.id}").authAs(admin)).andExpect(status().isBadRequest)
        assertTrue(userRepository.findById(foreignWaiter.id!!).orElseThrow().active)
    }

    // --- Menu ------------------------------------------------------------------

    @Test
    fun `CRUD de menu con baja logica`() {
        val id = mockMvc.perform(
            post("/api/admin/menu").authAs(admin).json(
                mapOf("name" to "Tiramisu", "category" to "dessert", "price" to 14.0, "protein" to "Ninguna")
            )
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.category").value("DESSERT"))
            .andReturn().body()["id"].asLong()

        mockMvc.perform(
            put("/api/admin/menu/$id").authAs(admin).json(mapOf("name" to "Tiramisu Casero", "category" to "DESSERT", "price" to 15.5))
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.price").value(15.5))

        val waiter = data.user(Role.EMPLOYEE, restaurant)
        mockMvc.perform(get("/api/staff/menu").authAs(waiter)).andExpect(jsonPath("$.length()").value(1))

        mockMvc.perform(delete("/api/admin/menu/$id").authAs(admin)).andExpect(status().isNoContent)
        mockMvc.perform(get("/api/staff/menu").authAs(waiter)).andExpect(jsonPath("$.length()").value(0))
        // Baja logica: el registro sigue existiendo para los pedidos historicos.
        assertFalse(menuItemRepository.findById(id).orElseThrow().active)
    }

    @Test
    fun `menu rechaza categoria invalida, precio negativo y edicion entre restaurantes`() {
        mockMvc.perform(post("/api/admin/menu").authAs(admin).json(mapOf("name" to "X", "category" to "SNACK", "price" to 1.0)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Invalid category: SNACK"))
        mockMvc.perform(post("/api/admin/menu").authAs(admin).json(mapOf("name" to "X", "price" to -1.0)))
            .andExpect(status().isBadRequest)

        val dish = data.menuItem(restaurant)
        mockMvc.perform(put("/api/admin/menu/${dish.id}").authAs(foreignAdmin).json(mapOf("name" to "Hackeado", "price" to 0.0)))
            .andExpect(status().isBadRequest)
        assertEquals(dish.name, menuItemRepository.findById(dish.id!!).orElseThrow().name)
    }

    // --- Mesas -------------------------------------------------------------------

    @Test
    fun `CRUD de mesas con zona propia y baja logica`() {
        val zone = data.zone(restaurant)
        val id = mockMvc.perform(
            post("/api/employee/tables").authAs(admin).json(
                mapOf("tableNumber" to 7, "floor" to 1, "capacity" to 6, "price" to 80.0, "zoneId" to zone.id, "gridX" to 2, "gridY" to 3)
            )
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.zoneName").value(zone.name))
            .andReturn().body()["id"].asLong()

        mockMvc.perform(
            put("/api/employee/tables/$id").authAs(admin).json(
                mapOf("tableNumber" to 7, "floor" to 1, "capacity" to 6, "price" to 80.0, "gridX" to 4, "gridY" to 1)
            )
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.gridX").value(4))
            .andExpect(jsonPath("$.zoneId").doesNotExist())

        mockMvc.perform(delete("/api/employee/tables/$id").authAs(admin)).andExpect(status().isNoContent)
        mockMvc.perform(get("/api/staff/tables").authAs(admin)).andExpect(jsonPath("$.length()").value(0))
        assertFalse(tableRepository.findById(id).orElseThrow().active)
    }

    @Test
    fun `mesas de otro restaurante no se leen ni modifican y no se usan zonas ajenas`() {
        val foreignRestaurant = foreignAdmin.restaurant!!
        val foreignTable = data.table(foreignRestaurant)
        val foreignZone = data.zone(foreignRestaurant)

        mockMvc.perform(get("/api/employee/tables/${foreignTable.id}").authAs(admin)).andExpect(status().isNotFound)
        mockMvc.perform(delete("/api/employee/tables/${foreignTable.id}").authAs(admin)).andExpect(status().isNotFound)
        mockMvc.perform(
            post("/api/employee/tables").authAs(admin).json(
                mapOf("tableNumber" to 1, "floor" to 1, "capacity" to 2, "price" to 10.0, "zoneId" to foreignZone.id)
            )
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Zone not found for restaurant"))

        assertTrue(tableRepository.findById(foreignTable.id!!).orElseThrow().active)
    }

    // --- Vistas globales del admin (regresion QA-SEC-01) ---------------------

    @Test
    fun `pedidos y reservas del panel admin se limitan al restaurante propio`() {
        val ownTable = data.table(restaurant)
        val ownWaiter = data.user(Role.EMPLOYEE, restaurant)
        val ownDish = data.menuItem(restaurant)
        val foreignRestaurant = foreignAdmin.restaurant!!
        val foreignTable = data.table(foreignRestaurant)
        val foreignWaiter = data.user(Role.EMPLOYEE, foreignRestaurant)
        val foreignDish = data.menuItem(foreignRestaurant)

        fun order(by: User, tableId: Long?, dishId: Long?) =
            mockMvc.perform(
                post("/api/employee/orders").authAs(by).json(
                    mapOf("tableId" to tableId, "items" to listOf(mapOf("menuItemId" to dishId, "quantity" to 1)))
                )
            ).andExpect(status().isOk).andReturn().body()["id"].asLong()

        val ownOrder = order(ownWaiter, ownTable.id, ownDish.id)
        val foreignOrder = order(foreignWaiter, foreignTable.id, foreignDish.id)
        listOf(ownOrder to ownWaiter, foreignOrder to foreignWaiter).forEach { (id, by) ->
            mockMvc.perform(put("/api/employee/orders/$id/status").param("status", "IN_PROGRESS").authAs(by))
                .andExpect(status().isOk)
        }

        val reservationAt = java.time.LocalDateTime.now().plusDays(10).withHour(20).withMinute(0).withSecond(0).withNano(0)
        listOf(restaurant to ownTable, foreignRestaurant to foreignTable).forEach { (r, t) ->
            mockMvc.perform(
                post("/api/public/reservations").json(
                    mapOf(
                        "restaurantId" to r.id, "tableId" to t.id, "reservationDate" to reservationAt.toString(),
                        "numberOfGuests" to 2, "customerName" to "Cliente", "customerEmail" to "cliente${r.id}@example.test"
                    )
                )
            ).andExpect(status().isOk)
        }

        mockMvc.perform(get("/api/admin/orders").authAs(admin))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(ownOrder))
        mockMvc.perform(get("/api/admin/orders/active").authAs(admin))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(ownOrder))
        mockMvc.perform(get("/api/admin/reservations").authAs(admin))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].tableId").value(ownTable.id!!))
        // El otro admin tambien ve solo lo suyo.
        mockMvc.perform(get("/api/admin/orders").authAs(foreignAdmin))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(foreignOrder))
    }

    // --- Restaurantes ----------------------------------------------------------

    private fun registration(name: String, adminEmail: String) = mapOf(
        "name" to name, "description" to "Nuevo local", "address" to "Calle 2", "phone" to "+00 111",
        "email" to "local@example.test", "numberOfTables" to 5, "numberOfChairs" to 20, "numberOfFloors" to 2,
        "adminEmail" to adminEmail, "adminPassword" to TestData.PASSWORD, "adminName" to "Admin Nuevo"
    )

    @Test
    fun `registrar restaurante crea el admin, genera el sitio y devuelve su URL`() {
        mockMvc.perform(post("/api/admin/restaurants/register").authAs(admin).json(registration("Casa Nueva", "Admin.Nuevo@Example.test")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.slug").value("casa-nueva"))
            .andExpect(jsonPath("$.websiteUrl").value("/casa-nueva"))

        val created = restaurantRepository.findBySlug("casa-nueva").orElseThrow()
        assertEquals("/casa-nueva", created.websiteUrl)
        assertTrue(Files.exists(Paths.get("build/test-generated-websites/casa-nueva/index.html")))

        val newAdmin = userRepository.findByEmail("admin.nuevo@example.test").orElseThrow()
        assertEquals(Role.ADMIN, newAdmin.role)
        assertEquals(created.id, newAdmin.restaurant?.id)
        login("admin.nuevo@example.test").andExpect(status().isOk)
    }

    @Test
    fun `registrar restaurante con nombre repetido o sin letras responde 400`() {
        mockMvc.perform(post("/api/admin/restaurants/register").authAs(admin).json(registration(restaurant.name, "a1@example.test")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Restaurant name already exists"))
        mockMvc.perform(post("/api/admin/restaurants/register").authAs(admin).json(registration("!!!", "a2@example.test")))
            .andExpect(status().isBadRequest)
        // Email de administrador ya usado: 400 y sin restaurante huerfano.
        mockMvc.perform(post("/api/admin/restaurants/register").authAs(admin).json(registration("Casa Duplicada", admin.email.uppercase())))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Email already registered"))
        assertFalse(restaurantRepository.findBySlug("casa-duplicada").isPresent)
    }

    @Test
    fun `un admin solo lee, edita y desactiva su propio restaurante`() {
        val foreignId = foreignAdmin.restaurant!!.id
        mockMvc.perform(get("/api/admin/restaurants/$foreignId").authAs(admin)).andExpect(status().isNotFound)
        mockMvc.perform(put("/api/admin/restaurants/$foreignId").authAs(admin).json(registration("Robado", "r@example.test")))
            .andExpect(status().isNotFound)
        mockMvc.perform(delete("/api/admin/restaurants/$foreignId").authAs(admin)).andExpect(status().isNotFound)
        assertTrue(restaurantRepository.findById(foreignId!!).orElseThrow().active)

        mockMvc.perform(put("/api/admin/restaurants/${restaurant.id}").authAs(admin).json(registration(restaurant.name, "r@example.test")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.numberOfFloors").value(2))
        mockMvc.perform(delete("/api/admin/restaurants/${restaurant.id}").authAs(admin)).andExpect(status().isNoContent)
        assertFalse(restaurantRepository.findById(restaurant.id!!).orElseThrow().active)
        mockMvc.perform(get("/api/public/restaurants/${restaurant.slug}")).andExpect(status().is4xxClientError)
    }
}
