package com.restaurant.reservations.controller

import com.restaurant.reservations.dto.StaffRequest
import com.restaurant.reservations.dto.StaffResponse
import com.restaurant.reservations.service.StaffService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/staff")
@PreAuthorize("hasRole('ADMIN')")
class StaffController(
    private val staffService: StaffService
) {

    @GetMapping
    fun getStaff(): ResponseEntity<List<StaffResponse>> =
        ResponseEntity.ok(staffService.getStaff())

    @PostMapping
    fun createStaff(@Valid @RequestBody request: StaffRequest): ResponseEntity<StaffResponse> =
        ResponseEntity.ok(staffService.createStaff(request))

    @PutMapping("/{id}")
    fun updateStaff(
        @PathVariable id: Long,
        @Valid @RequestBody request: StaffRequest
    ): ResponseEntity<StaffResponse> =
        ResponseEntity.ok(staffService.updateStaff(id, request))

    @DeleteMapping("/{id}")
    fun deleteStaff(@PathVariable id: Long): ResponseEntity<Void> {
        staffService.deleteStaff(id)
        return ResponseEntity.noContent().build()
    }
}
