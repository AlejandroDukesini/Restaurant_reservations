package com.restaurant.reservations.repository

import com.restaurant.reservations.model.RestaurantTable
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface TableRepository : JpaRepository<RestaurantTable, Long> {
    fun findByRestaurantId(restaurantId: Long): List<RestaurantTable>
    fun findByRestaurantIdAndFloor(restaurantId: Long, floor: Int): List<RestaurantTable>
    fun findByRestaurantIdAndActiveTrue(restaurantId: Long): List<RestaurantTable>
    fun findByRestaurantIdAndFloorAndActiveTrue(restaurantId: Long, floor: Int): List<RestaurantTable>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RestaurantTable t where t.id = :tableId")
    fun findByIdForUpdate(@Param("tableId") tableId: Long): Optional<RestaurantTable>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RestaurantTable t where t.id = :tableId and t.restaurant.id = :restaurantId")
    fun findByIdAndRestaurantIdForUpdate(
        @Param("tableId") tableId: Long,
        @Param("restaurantId") restaurantId: Long
    ): Optional<RestaurantTable>
}
