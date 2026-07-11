package com.restaurant.reservations.controller

import com.restaurant.reservations.dto.MenuItemRequest
import com.restaurant.reservations.dto.MenuItemResponse
import com.restaurant.reservations.service.MenuService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class MenuController(
    private val menuService: MenuService
) {

    // Lectura compartida por todo el personal (empleado arma pedido, cocinero ve receta)
    @GetMapping("/staff/menu")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'COOK')")
    fun getMenu(): ResponseEntity<List<MenuItemResponse>> =
        ResponseEntity.ok(menuService.getMenu())

    @PostMapping("/admin/menu")
    @PreAuthorize("hasRole('ADMIN')")
    fun createMenuItem(@Valid @RequestBody request: MenuItemRequest): ResponseEntity<MenuItemResponse> =
        ResponseEntity.ok(menuService.createMenuItem(request))

    @PutMapping("/admin/menu/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun updateMenuItem(
        @PathVariable id: Long,
        @Valid @RequestBody request: MenuItemRequest
    ): ResponseEntity<MenuItemResponse> =
        ResponseEntity.ok(menuService.updateMenuItem(id, request))

    @DeleteMapping("/admin/menu/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun deleteMenuItem(@PathVariable id: Long): ResponseEntity<Void> {
        menuService.deleteMenuItem(id)
        return ResponseEntity.noContent().build()
    }
}
