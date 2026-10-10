package com.restaurant.reservations.controller;

import com.restaurant.reservations.dto.CookQueueItem;
import com.restaurant.reservations.service.CookService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cook")
@PreAuthorize("hasAnyRole('ADMIN', 'COOK')")
public class CookController {

    private final CookService cookService;

    public CookController(CookService cookService) {
        this.cookService = cookService;
    }

    @GetMapping("/queue")
    public ResponseEntity<List<CookQueueItem>> getQueue() {
        return ResponseEntity.ok(cookService.getQueue());
    }

    @PutMapping("/order-items/{id}/status")
    public ResponseEntity<CookQueueItem> updateItemStatus(@PathVariable Long id, @RequestParam String status) {
        return ResponseEntity.ok(cookService.updateItemStatus(id, status));
    }
}
