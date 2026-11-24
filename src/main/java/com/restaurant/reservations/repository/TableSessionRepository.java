package com.restaurant.reservations.repository;

import com.restaurant.reservations.model.TableSession;
import com.restaurant.reservations.model.TableSessionStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TableSessionRepository extends JpaRepository<TableSession, Long> {
    @Query("SELECT s FROM TableSession s WHERE s.table.id = :tableId AND s.status <> :closed")
    Optional<TableSession> findOpenByTable(@Param("tableId") Long tableId,
                                           @Param("closed") TableSessionStatus closed);

    @Query("SELECT s FROM TableSession s WHERE s.restaurantId = :restaurantId AND s.status <> :closed")
    List<TableSession> findOpenByRestaurant(@Param("restaurantId") Long restaurantId,
                                            @Param("closed") TableSessionStatus closed);

    List<TableSession> findByRestaurantIdAndOpenedAtGreaterThanEqualAndOpenedAtLessThan(
        Long restaurantId, LocalDateTime from, LocalDateTime to);

    boolean existsByTableId(Long tableId);
}
