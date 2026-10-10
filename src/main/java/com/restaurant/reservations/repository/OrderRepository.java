package com.restaurant.reservations.repository;

import com.restaurant.reservations.model.Order;
import com.restaurant.reservations.model.OrderStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByEmployeeId(Long employeeId);

    List<Order> findByTableId(Long tableId);

    List<Order> findByStatus(OrderStatus status);

    Optional<Order> findByIdAndEmployeeId(Long id, Long employeeId);

    @Query("SELECT o FROM Order o WHERE o.table.restaurant.id = :restaurantId")
    List<Order> findByRestaurantId(@Param("restaurantId") Long restaurantId);

    @Query("SELECT o FROM Order o WHERE o.table.restaurant.id = :restaurantId AND o.status = :status")
    List<Order> findByRestaurantIdAndStatus(
        @Param("restaurantId") Long restaurantId,
        @Param("status") OrderStatus status
    );

    // Cola de cocina: pedidos del restaurante que no están terminados/cancelados.
    // JOIN FETCH resuelve en UNA sola consulta todo lo que la cola necesita renderizar
    // (platos, su receta, mesa, restaurante y mesero), eliminando el N+1 que generaba
    // el acceso lazy a o.items / o.table.restaurant / o.employee / item.menuItem.
    @Query(
        "SELECT DISTINCT o FROM Order o "
            + "LEFT JOIN FETCH o.items i "
            + "LEFT JOIN FETCH i.menuItem "
            + "JOIN FETCH o.table t "
            + "JOIN FETCH t.restaurant "
            + "JOIN FETCH o.employee "
            + "WHERE t.restaurant.id = :restaurantId "
            + "AND o.status IN :statuses ORDER BY o.orderDate ASC"
    )
    List<Order> findActiveByRestaurant(
        @Param("restaurantId") Long restaurantId,
        @Param("statuses") List<OrderStatus> statuses
    );
}
