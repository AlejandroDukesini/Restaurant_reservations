package com.restaurant.reservations.repository

import com.restaurant.reservations.model.Order
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface OrderRepository : JpaRepository<Order, Long> {
    fun findByEmployeeId(employeeId: Long): List<Order>
    fun findByTableId(tableId: Long): List<Order>
    fun findByRestaurantId(restaurantId: Long): List<Order>
    fun findByStatus(status: com.restaurant.reservations.model.OrderStatus): List<Order>
    fun findByIdAndEmployeeId(id: Long, employeeId: Long): Optional<Order>
}
