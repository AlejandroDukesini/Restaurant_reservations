package com.restaurant.reservations.repository;

import com.restaurant.reservations.model.Order;
import com.restaurant.reservations.model.OrderChannel;
import com.restaurant.reservations.model.OrderStatus;
import java.time.LocalDateTime;
import java.util.Collection;
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

    Optional<Order> findByIdAndRestaurantId(Long id, Long restaurantId);

    boolean existsByTableId(Long tableId);

    List<Order> findByRestaurantIdOrderByOrderDateDesc(Long restaurantId);

    List<Order> findByRestaurantIdAndStatus(Long restaurantId, OrderStatus status);

    List<Order> findBySessionIdOrderByIdAsc(Long sessionId);

    @Query("SELECT o FROM Order o WHERE o.session.id IN :sessionIds")
    List<Order> findBySessionIds(@Param("sessionIds") Collection<Long> sessionIds);

    // Cola de cocina: JOIN FETCH resuelve en una consulta platos, receta, mesa y mesero (sin N+1).
    @Query(
        "SELECT DISTINCT o FROM Order o "
            + "LEFT JOIN FETCH o.items i "
            + "LEFT JOIN FETCH i.menuItem "
            + "LEFT JOIN FETCH o.table t "
            + "JOIN FETCH o.employee "
            + "WHERE o.restaurantId = :restaurantId "
            + "AND o.status IN :statuses ORDER BY o.orderDate ASC"
    )
    List<Order> findActiveByRestaurant(
        @Param("restaurantId") Long restaurantId,
        @Param("statuses") Collection<OrderStatus> statuses
    );

    @Query("SELECT o FROM Order o WHERE o.restaurantId = :restaurantId AND o.channel = :channel "
        + "AND (o.status NOT IN :finals OR o.orderDate >= :since) ORDER BY o.orderDate DESC")
    List<Order> findChannelBoard(
        @Param("restaurantId") Long restaurantId,
        @Param("channel") OrderChannel channel,
        @Param("finals") Collection<OrderStatus> finals,
        @Param("since") LocalDateTime since
    );

    @Query("SELECT o FROM Order o WHERE o.driver.id = :driverId "
        + "AND (o.status NOT IN :finals OR o.orderDate >= :since) ORDER BY o.orderDate DESC")
    List<Order> findDriverBoard(
        @Param("driverId") Long driverId,
        @Param("finals") Collection<OrderStatus> finals,
        @Param("since") LocalDateTime since
    );

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.restaurantId = :restaurantId "
        + "AND o.orderDate >= :from AND o.orderDate < :to")
    List<Order> findForMetrics(
        @Param("restaurantId") Long restaurantId,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to
    );
}
