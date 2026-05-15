package com.restaurant.reservations.controller

import com.restaurant.reservations.dto.TableRequest
import com.restaurant.reservations.dto.TableResponse
import com.restaurant.reservations.service.TableService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class TableController(
    private val tableService: TableService
) {
    
    @PostMapping("/employee/tables")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    fun createTable(@RequestBody request: TableRequest): ResponseEntity<TableResponse> {
        val table = tableService.createTable(request)
        return ResponseEntity.ok(table)
    }
    
    @GetMapping("/public/restaurants/{restaurantId}/tables")
    fun getTablesByRestaurant(
        @PathVariable restaurantId: Long,
        @RequestParam(required = false) floor: Int?
    ): ResponseEntity<List<TableResponse>> {
        val tables = if (floor != null) {
            tableService.getTablesByFloor(restaurantId, floor)
        } else {
            tableService.getTablesByRestaurant(restaurantId)
        }
        return ResponseEntity.ok(tables)
    }
    
    @GetMapping("/employee/tables/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    fun getTableById(@PathVariable id: Long): ResponseEntity<TableResponse> {
        val table = tableService.getTableById(id)
        return ResponseEntity.ok(table)
    }
    
    @PutMapping("/employee/tables/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    fun updateTable(
        @PathVariable id: Long,
        @RequestBody request: TableRequest
    ): ResponseEntity<TableResponse> {
        val table = tableService.updateTable(id, request)
        return ResponseEntity.ok(table)
    }
    
    @DeleteMapping("/employee/tables/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    fun deleteTable(@PathVariable id: Long): ResponseEntity<Void> {
        tableService.deleteTable(id)
        return ResponseEntity.noContent().build()
    }
}
