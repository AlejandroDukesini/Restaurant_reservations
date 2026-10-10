package com.restaurant.reservations.service

import com.restaurant.reservations.dto.OrderRequest
import com.restaurant.reservations.dto.OrderResponse
import com.restaurant.reservations.exception.ResourceNotFoundException
import com.restaurant.reservations.exception.ValidationBusinessException
import com.restaurant.reservations.model.Order
import com.restaurant.reservations.model.OrderItem
import com.restaurant.reservations.model.OrderStatus
import com.restaurant.reservations.model.Role
import com.restaurant.reservations.repository.MenuItemRepository
import com.restaurant.reservations.repository.OrderItemRepository
import com.restaurant.reservations.repository.OrderRepository
import com.restaurant.reservations.repository.TableRepository
import com.restaurant.reservations.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private const val MAX_ITEMS_PER_ORDER = 100

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val tableRepository: TableRepository,
    private val userRepository: UserRepository,
    private val menuItemRepository: MenuItemRepository,
    private val authService: AuthService
) {

    private val log = LoggerFactory.getLogger(OrderService::class.java)

    @Transactional
    fun createOrder(request: OrderRequest): OrderResponse {
        val employeeId = authService.getCurrentUserId()
        val restaurantId = currentRestaurantId()

        val table = tableRepository.findById(request.tableId)
            .orElseThrow { ResourceNotFoundException("Table not found") }

        // La mesa debe ser del restaurante del mesero: sin esto se podian crear
        // pedidos sobre las mesas de otro restaurante (cross-tenant).
        requireSameRestaurant(table.restaurant.id, restaurantId, "Table not found")

        val employee = userRepository.findById(employeeId)
            .orElseThrow { ResourceNotFoundException("Employee not found") }

        require(request.items.isNotEmpty()) { "Order must have at least one item" }
        require(request.items.size <= MAX_ITEMS_PER_ORDER) {
            "Order cannot have more than $MAX_ITEMS_PER_ORDER items"
        }

        // Los platos son de menú (no personalizables): nombre y precio se toman del MenuItem.
        val orderItems = request.items.map { itemRequest ->
            val menuItem = menuItemRepository.findById(itemRequest.menuItemId)
                .orElseThrow { ResourceNotFoundException("Menu item not found") }
            // Un plato de la carta de otro restaurante no puede entrar en este pedido.
            requireSameRestaurant(menuItem.restaurant.id, restaurantId, "Menu item not found")
            OrderItem(
                order = null, // Will be set after order is saved
                menuItem = menuItem,
                itemName = menuItem.name,
                quantity = itemRequest.quantity,
                price = menuItem.price,
                notes = itemRequest.notes
            )
        }

        val totalAmount = orderItems.sumOf { it.price * it.quantity }
        
        val order = Order(
            table = table,
            employee = employee,
            totalAmount = totalAmount,
            notes = request.notes
        )
        
        val savedOrder = orderRepository.save(order)
        
        val savedOrderItems = orderItems.map { it.copy(order = savedOrder) }
        orderItemRepository.saveAll(savedOrderItems)
        
        return toResponse(savedOrder.copy(items = savedOrderItems))
    }
    
    fun getMyOrders(): List<OrderResponse> {
        val employeeId = authService.getCurrentUserId()
        return orderRepository.findByEmployeeId(employeeId)
            .map { toResponse(it) }
    }
    
    fun getOrdersByTable(tableId: Long): List<OrderResponse> {
        val restaurantId = currentRestaurantId()
        val table = tableRepository.findById(tableId)
            .orElseThrow { ResourceNotFoundException("Table not found") }
        requireSameRestaurant(table.restaurant.id, restaurantId, "Table not found")
        return orderRepository.findByTableId(tableId)
            .map { toResponse(it) }
    }

    fun getOrderById(id: Long): OrderResponse {
        val order = orderRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Order not found") }
        // Sin este control, un mesero podia leer los pedidos de cualquier otro
        // restaurante iterando el id (IDOR).
        requireSameRestaurant(order.table.restaurant.id, currentRestaurantId(), "Order not found")
        return toResponse(order)
    }

    @Transactional
    fun updateOrderStatus(id: Long, status: String): OrderResponse {
        val order = orderRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Order not found") }

        requireCanModify(order)

        val newStatus = try {
            OrderStatus.valueOf(status.uppercase())
        } catch (ex: IllegalArgumentException) {
            throw ValidationBusinessException("Invalid order status")
        }

        val updatedOrder = order.copy(status = newStatus)

        return toResponse(orderRepository.save(updatedOrder))
    }

    @Transactional
    fun deleteOrder(id: Long) {
        val order = orderRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Order not found") }

        requireCanModify(order)

        log.info(
            "Pedido {} eliminado por userId={} role={}",
            id, authService.getCurrentUserId(), authService.getCurrentUserRole()
        )
        orderRepository.delete(order)
    }

    /**
     * Modificar/borrar un pedido: su autor, o un ADMIN del mismo restaurante.
     * El chequeo de tenant es obligatorio incluso para ADMIN, porque el rol ADMIN
     * lo tiene el administrador de cada restaurante, no un superusuario global.
     */
    private fun requireCanModify(order: Order) {
        val restaurantId = currentRestaurantId()
        requireSameRestaurant(order.table.restaurant.id, restaurantId, "Order not found")

        val employeeId = authService.getCurrentUserId()
        if (order.employee.id != employeeId && authService.getCurrentUserRole() != Role.ADMIN) {
            log.warn("Acceso denegado al pedido {}: userId={}", order.id, employeeId)
            throw ResourceNotFoundException("Order not found")
        }
    }

    private fun currentRestaurantId(): Long =
        authService.getCurrentUserRestaurantId()
            ?: throw ValidationBusinessException("User not associated with a restaurant")

    private fun requireSameRestaurant(ownerRestaurantId: Long?, callerRestaurantId: Long, message: String) {
        if (ownerRestaurantId != callerRestaurantId) {
            log.warn(
                "Acceso cross-tenant denegado: userId={} (restaurante {}) pidio recurso del restaurante {}",
                authService.getCurrentUserId(), callerRestaurantId, ownerRestaurantId
            )
            // 404 en vez de 403: no confirma la existencia de recursos ajenos.
            throw ResourceNotFoundException(message)
        }
    }


    private fun toResponse(order: Order): OrderResponse {
        return OrderResponse(
            id = order.id!!,
            tableId = order.table.id!!,
            tableNumber = order.table.tableNumber,
            employeeId = order.employee.id!!,
            employeeName = order.employee.name,
            orderDate = order.orderDate,
            status = order.status.name,
            totalAmount = order.totalAmount,
            items = order.items.map { item ->
                com.restaurant.reservations.dto.OrderItemResponse(
                    id = item.id!!,
                    menuItemId = item.menuItem?.id,
                    itemName = item.itemName,
                    quantity = item.quantity,
                    price = item.price,
                    status = item.status.name,
                    notes = item.notes
                )
            },
            notes = order.notes
        )
    }
}
