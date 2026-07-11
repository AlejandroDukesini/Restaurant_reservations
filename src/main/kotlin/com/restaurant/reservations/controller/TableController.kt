package com.restaurant.reservations.controller

import com.restaurant.reservations.dto.TableRequest
import com.restaurant.reservations.dto.TableResponse
import com.restaurant.reservations.service.TableService
import jakarta.validation.Valid
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
    fun createTable(@Valid @RequestBody request: TableRequest): ResponseEntity<TableResponse> {
        val table = tableService.createTable(request)
        return ResponseEntity.ok(table)
    }
    
    // Mesas del restaurante del usuario autenticado (personal: mesero, cocinero, admin).
    @GetMapping("/staff/tables")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'COOK')")
    fun getMyTables(
        @RequestParam(required = false) floor: Int?
    ): ResponseEntity<List<TableResponse>> {
        return ResponseEntity.ok(tableService.getMyTables(floor))
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
        @Valid @RequestBody request: TableRequest
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
