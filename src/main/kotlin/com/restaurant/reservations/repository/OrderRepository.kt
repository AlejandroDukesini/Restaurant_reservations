package com.restaurant.reservations.repository

import com.restaurant.reservations.model.Order
import com.restaurant.reservations.model.OrderStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface OrderRepository : JpaRepository<Order, Long> {
    fun findByEmployeeId(employeeId: Long): List<Order>
    fun findByTableId(tableId: Long): List<Order>
    fun findByStatus(status: OrderStatus): List<Order>
    fun findByIdAndEmployeeId(id: Long, employeeId: Long): Optional<Order>

    @Query("SELECT o FROM Order o WHERE o.table.restaurant.id = :restaurantId")
    fun findByRestaurantId(@Param("restaurantId") restaurantId: Long): List<Order>

    // Cola de cocina: pedidos del restaurante que no están terminados/cancelados.
    // JOIN FETCH resuelve en UNA sola consulta todo lo que la cola necesita renderizar
    // (platos, su receta, mesa, restaurante y mesero), eliminando el N+1 que generaba
    // el acceso lazy a o.items / o.table.restaurant / o.employee / item.menuItem.
    @Query(
        "SELECT DISTINCT o FROM Order o " +
            "LEFT JOIN FETCH o.items i " +
            "LEFT JOIN FETCH i.menuItem " +
            "JOIN FETCH o.table t " +
            "JOIN FETCH t.restaurant " +
            "JOIN FETCH o.employee " +
            "WHERE t.restaurant.id = :restaurantId " +
            "AND o.status IN :statuses ORDER BY o.orderDate ASC"
    )
    fun findActiveByRestaurant(
        @Param("restaurantId") restaurantId: Long,
        @Param("statuses") statuses: List<OrderStatus>
    ): List<Order>
}
