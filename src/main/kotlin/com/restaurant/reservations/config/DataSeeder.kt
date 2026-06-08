package com.restaurant.reservations.config

import com.restaurant.reservations.model.Restaurant
import com.restaurant.reservations.model.RestaurantTable
import com.restaurant.reservations.model.Zone
import com.restaurant.reservations.repository.RestaurantRepository
import com.restaurant.reservations.repository.TableRepository
import com.restaurant.reservations.repository.ZoneRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class DataSeeder(
    private val restaurantRepository: RestaurantRepository,
    private val zoneRepository: ZoneRepository,
    private val tableRepository: TableRepository
) : ApplicationRunner {

    @Transactional
    override fun run(args: ApplicationArguments) {
        if (restaurantRepository.count() > 0) return

        val restaurant = restaurantRepository.save(
            Restaurant(
                name = "Maison Noir",
                slug = "maison-noir",
                description = "Experiencia gastronómica premium multi-tenant",
                address = "Av. Premium 1200, Ciudad",
                phone = "+57 300 000 0000",
                email = "reservas@maisonnoir.com",
                numberOfTables = 16,
                numberOfChairs = 56,
                numberOfFloors = 1,
                websiteUrl = "https://maisonnoir.com"
            )
        )

        val zoneSpecs = listOf(
            Triple("Vistas a la Ventana", "WINDOW", 1),
            Triple("Cerca de la Cocina", "KITCHEN", 2),
            Triple("Zona Central", "CENTRAL", 3),
            Triple("Barra de Cócteles", "BAR", 4),
            Triple("Terraza Exterior", "TERRACE", 5)
        )

        val zones = zoneSpecs.map { (name, code, order) ->
            zoneRepository.save(
                Zone(
                    name = name,
                    code = code,
                    sortOrder = order,
                    restaurant = restaurant
                )
            )
        }

        val tableSpecs = listOf(
            TableSeed(1, 2, 1, 1, 0),
            TableSeed(2, 2, 2, 1, 0),
            TableSeed(3, 4, 3, 1, 0),
            TableSeed(4, 4, 1, 1, 1),
            TableSeed(5, 6, 2, 1, 1),
            TableSeed(6, 2, 3, 1, 1),
            TableSeed(7, 4, 1, 1, 2),
            TableSeed(8, 4, 2, 1, 2),
            TableSeed(9, 8, 3, 1, 2),
            TableSeed(10, 6, 2, 2, 2),
            TableSeed(11, 2, 1, 1, 3),
            TableSeed(12, 2, 2, 1, 3),
            TableSeed(13, 2, 3, 1, 3),
            TableSeed(14, 4, 1, 1, 4),
            TableSeed(15, 6, 2, 1, 4),
            TableSeed(16, 2, 3, 1, 4)
        )

        tableSpecs.forEach { spec ->
            tableRepository.save(
                RestaurantTable(
                    tableNumber = spec.tableNumber,
                    floor = 1,
                    capacity = spec.capacity,
                    price = when {
                        spec.capacity <= 2 -> 45.0
                        spec.capacity <= 4 -> 65.0
                        else -> 95.0
                    },
                    zone = zones[spec.zoneIndex],
                    gridX = spec.gridX,
                    gridY = spec.gridY,
                    restaurant = restaurant
                )
            )
        }
    }

    private data class TableSeed(
        val tableNumber: Int,
        val capacity: Int,
        val gridX: Int,
        val gridY: Int,
        val zoneIndex: Int
    )
}
