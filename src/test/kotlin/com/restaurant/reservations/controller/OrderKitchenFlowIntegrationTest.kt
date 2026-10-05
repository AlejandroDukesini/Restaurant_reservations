package com.restaurant.reservations.controller

import com.restaurant.reservations.model.MenuItem
import com.restaurant.reservations.model.OrderStatus
import com.restaurant.reservations.model.Restaurant
import com.restaurant.reservations.model.RestaurantTable
import com.restaurant.reservations.model.Role
import com.restaurant.reservations.model.User
import com.restaurant.reservations.repository.OrderRepository
import com.restaurant.reservations.support.IntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Flujo principal de la aplicacion: el mesero crea un pedido, la cocina lo ve en
 * la cola con la receta y marca cada plato; el estado del pedido se deriva de sus platos.
 */
class OrderKitchenFlowIntegrationTest : IntegrationTest() {

    @Autowired private lateinit var orderRepository: OrderRepository

    private lateinit var restaurant: Restaurant
    private lateinit var table: RestaurantTable
    private lateinit var waiter: User
    private lateinit var cook: User
    private lateinit var risotto: MenuItem
    private lateinit var wine: MenuItem

    @BeforeEach
    fun setUp() {
        restaurant = data.restaurant()
        table = data.table(restaurant)
        waiter = data.user(Role.EMPLOYEE, restaurant)
        cook = data.user(Role.COOK, restaurant)
        risotto = data.menuItem(restaurant, name = "Risotto", price = 32.0)
        wine = data.menuItem(restaurant, name = "Vino", price = 12.5)
    }

    private fun createOrder(by: User = waiter, tableId: Long = table.id!!, vararg items: Pair<Long, Int>) =
        mockMvc.perform(
            post("/api/employee/orders").authAs(by).json(
                mapOf(
                    "tableId" to tableId,
                    "items" to items.map { (menuItemId, qty) -> mapOf("menuItemId" to menuItemId, "quantity" to qty) },
                    "notes" to "Sin prisa"
                )
            )
        )

    private fun createDefaultOrder(): Long =
        createOrder(items = arrayOf(risotto.id!! to 2, wine.id!! to 1))
            .andExpect(status().isOk).andReturn().body()["id"].asLong()

    @Test
    fun `el pedido toma nombre y precio del menu y calcula el total en el servidor`() {
        createOrder(items = arrayOf(risotto.id!! to 2, wine.id!! to 1))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.totalAmount").value(76.5)) // 32*2 + 12.5
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].itemName").value("Risotto"))
            .andExpect(jsonPath("$.items[0].price").value(32.0))
            .andExpect(jsonPath("$.employeeId").value(waiter.id!!))
    }

    @Test
    fun `no se pueden pedir platos ni usar mesas de otro restaurante`() {
        val other = data.restaurant()
        val foreignDish = data.menuItem(other)
        val foreignTable = data.table(other)

        createOrder(items = arrayOf(foreignDish.id!! to 1)).andExpect(status().isNotFound)
        createOrder(tableId = foreignTable.id!!, items = arrayOf(risotto.id!! to 1)).andExpect(status().isNotFound)
        assertEquals(0, orderRepository.count())
    }

    @Test
    fun `cantidad fuera de rango se rechaza antes de llegar al servicio`() {
        createOrder(items = arrayOf(risotto.id!! to 0)).andExpect(status().isBadRequest)
        createOrder(items = arrayOf(risotto.id!! to 501)).andExpect(status().isBadRequest)
    }

    @Test
    fun `la cocina ve los platos con su receta y el estado del pedido sigue a los platos`() {
        val orderId = createDefaultOrder()

        val queue = mockMvc.perform(get("/api/cook/queue").authAs(cook))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].protein").value("Pollo"))
            .andExpect(jsonPath("$[0].tableNumber").value(table.tableNumber))
            .andReturn().body()
        val itemIds = queue.map { it["orderItemId"].asLong() }

        // Un plato en preparacion -> pedido en curso.
        mockMvc.perform(put("/api/cook/order-items/${itemIds[0]}/status").param("status", "preparing").authAs(cook))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("PREPARING"))
        assertEquals(OrderStatus.IN_PROGRESS, orderRepository.findById(orderId).orElseThrow().status)

        // Un plato listo sale de la cola, pero el pedido sigue en curso.
        mockMvc.perform(put("/api/cook/order-items/${itemIds[0]}/status").param("status", "READY").authAs(cook))
            .andExpect(status().isOk)
        mockMvc.perform(get("/api/cook/queue").authAs(cook)).andExpect(jsonPath("$.length()").value(1))
        assertEquals(OrderStatus.IN_PROGRESS, orderRepository.findById(orderId).orElseThrow().status)

        // Todos listos -> pedido completado y cola vacia.
        mockMvc.perform(put("/api/cook/order-items/${itemIds[1]}/status").param("status", "READY").authAs(cook))
            .andExpect(status().isOk)
        assertEquals(OrderStatus.COMPLETED, orderRepository.findById(orderId).orElseThrow().status)
        mockMvc.perform(get("/api/cook/queue").authAs(cook)).andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `un pedido cancelado no se reabre al cambiar sus platos`() {
        val orderId = createDefaultOrder()
        mockMvc.perform(put("/api/employee/orders/$orderId/status").param("status", "CANCELLED").authAs(waiter))
            .andExpect(status().isOk)

        val itemId = mockMvc.perform(get("/api/employee/orders/$orderId").authAs(waiter))
            .andReturn().body()["items"][0]["id"].asLong()
        mockMvc.perform(put("/api/cook/order-items/$itemId/status").param("status", "PREPARING").authAs(cook))
            .andExpect(status().isOk)

        assertEquals(OrderStatus.CANCELLED, orderRepository.findById(orderId).orElseThrow().status)
    }

    @Test
    fun `la cocina de otro restaurante no puede tocar los platos`() {
        createDefaultOrder()
        val itemId = mockMvc.perform(get("/api/cook/queue").authAs(cook)).andReturn().body()[0]["orderItemId"].asLong()
        val foreignCook = data.user(Role.COOK, data.restaurant())

        mockMvc.perform(get("/api/cook/queue").authAs(foreignCook)).andExpect(jsonPath("$.length()").value(0))
        mockMvc.perform(put("/api/cook/order-items/$itemId/status").param("status", "READY").authAs(foreignCook))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Unauthorized access to order item"))
    }

    @Test
    fun `estado de plato desconocido responde 400`() {
        createDefaultOrder()
        val itemId = mockMvc.perform(get("/api/cook/queue").authAs(cook)).andReturn().body()[0]["orderItemId"].asLong()

        mockMvc.perform(put("/api/cook/order-items/$itemId/status").param("status", "QUEMADO").authAs(cook))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `parametro de estado ausente responde 400 y no 500`() {
        val orderId = createDefaultOrder()
        mockMvc.perform(put("/api/employee/orders/$orderId/status").authAs(waiter))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `id no numerico responde 400 y no 500`() {
        mockMvc.perform(get("/api/employee/orders/abc").authAs(waiter))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `solo el autor o un admin del restaurante modifican el pedido`() {
        val orderId = createDefaultOrder()
        val otherWaiter = data.user(Role.EMPLOYEE, restaurant)
        val admin = data.user(Role.ADMIN, restaurant)
        val foreignAdmin = data.user(Role.ADMIN, data.restaurant())

        mockMvc.perform(put("/api/employee/orders/$orderId/status").param("status", "COMPLETED").authAs(otherWaiter))
            .andExpect(status().isNotFound)
        mockMvc.perform(delete("/api/employee/orders/$orderId").authAs(foreignAdmin))
            .andExpect(status().isNotFound)
        mockMvc.perform(get("/api/employee/orders/$orderId").authAs(foreignAdmin))
            .andExpect(status().isNotFound)

        mockMvc.perform(put("/api/employee/orders/$orderId/status").param("status", "BOGUS").authAs(waiter))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Invalid order status"))

        mockMvc.perform(delete("/api/employee/orders/$orderId").authAs(admin))
            .andExpect(status().isNoContent)
        assertEquals(0, orderRepository.count())
    }

    @Test
    fun `pedidos por mesa y mis pedidos devuelven solo lo propio`() {
        createDefaultOrder()
        val otherWaiter = data.user(Role.EMPLOYEE, restaurant)

        mockMvc.perform(get("/api/employee/tables/${table.id}/orders").authAs(otherWaiter))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
        mockMvc.perform(get("/api/employee/orders").authAs(waiter))
            .andExpect(jsonPath("$.length()").value(1))
        mockMvc.perform(get("/api/employee/orders").authAs(otherWaiter))
            .andExpect(jsonPath("$.length()").value(0))
    }
}
