package com.restaurant.reservations.controller

import com.restaurant.reservations.dto.CookQueueItem
import com.restaurant.reservations.service.CookService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/cook")
@PreAuthorize("hasAnyRole('ADMIN', 'COOK')")
class CookController(
    private val cookService: CookService
) {

    @GetMapping("/queue")
    fun getQueue(): ResponseEntity<List<CookQueueItem>> =
        ResponseEntity.ok(cookService.getQueue())

    @PutMapping("/order-items/{id}/status")
    fun updateItemStatus(
        @PathVariable id: Long,
        @RequestParam status: String
    ): ResponseEntity<CookQueueItem> =
        ResponseEntity.ok(cookService.updateItemStatus(id, status))
}
