package com.restaurant.reservations.controller;

import com.restaurant.reservations.dto.PublicReservationRequest;
import com.restaurant.reservations.dto.ReservationRequest;
import com.restaurant.reservations.dto.ReservationResponse;
import com.restaurant.reservations.dto.TableMapResponse;
import com.restaurant.reservations.service.ReservationService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping("/public/restaurants/{restaurantId}/table-map")
    public ResponseEntity<TableMapResponse> getTableMap(
        @PathVariable Long restaurantId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time
    ) {
        return ResponseEntity.ok(reservationService.getTableMap(restaurantId, date, time));
    }

    @PostMapping("/public/reservations")
    public ResponseEntity<ReservationResponse> createPublicReservation(
        @Valid @RequestBody PublicReservationRequest request
    ) {
        return ResponseEntity.ok(reservationService.createPublicReservation(request));
    }

    @PostMapping("/customer/reservations")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'CUSTOMER')")
    public ResponseEntity<ReservationResponse> createReservation(@Valid @RequestBody ReservationRequest request) {
        return ResponseEntity.ok(reservationService.createReservation(request));
    }

    @GetMapping("/customer/reservations")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'CUSTOMER')")
    public ResponseEntity<List<ReservationResponse>> getMyReservations() {
        return ResponseEntity.ok(reservationService.getMyReservations());
    }

    // Deja de ser anonimo: devolvia nombre del cliente y peticiones especiales
    // (datos personales) de todas las reservas de una mesa sin autenticacion.
    // Se mantiene la ruta para no romper clientes existentes; el control pasa a
    // ser de metodo, que se aplica aunque /api/public/** siga siendo permitAll.
    @GetMapping("/public/tables/{tableId}/reservations")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'COOK')")
    public ResponseEntity<List<ReservationResponse>> getReservationsByTable(@PathVariable Long tableId) {
        return ResponseEntity.ok(reservationService.getReservationsByTable(tableId));
    }

    @GetMapping("/customer/reservations/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'CUSTOMER')")
    public ResponseEntity<ReservationResponse> getReservationById(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.getReservationById(id));
    }

    @PutMapping("/customer/reservations/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'CUSTOMER')")
    public ResponseEntity<ReservationResponse> updateReservation(
        @PathVariable Long id,
        @Valid @RequestBody ReservationRequest request
    ) {
        return ResponseEntity.ok(reservationService.updateReservation(id, request));
    }

    @PutMapping("/customer/reservations/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'CUSTOMER')")
    public ResponseEntity<ReservationResponse> confirmReservation(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.confirmReservation(id));
    }

    @PutMapping("/customer/reservations/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'CUSTOMER')")
    public ResponseEntity<ReservationResponse> cancelReservation(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.cancelReservation(id));
    }

    @DeleteMapping("/customer/reservations/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'CUSTOMER')")
    public ResponseEntity<Void> deleteReservation(@PathVariable Long id) {
        reservationService.deleteReservation(id);
        return ResponseEntity.noContent().build();
    }
}
