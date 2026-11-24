package com.restaurant.reservations.service;

import com.restaurant.reservations.dto.OrderResponse;
import com.restaurant.reservations.dto.ReservationResponse;
import com.restaurant.reservations.exception.ValidationBusinessException;
import com.restaurant.reservations.model.Reservation;
import com.restaurant.reservations.repository.OrderRepository;
import com.restaurant.reservations.repository.ReservationRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Vistas globales del panel de administracion, acotadas al restaurante del admin.
 *
 * ADMIN es el administrador de UN restaurante, no un superusuario: antes estas
 * consultas usaban findAll() y exponian pedidos y reservas (con nombres de
 * clientes) de todos los restaurantes (QA-SEC-01).
 */
@Service
public class AdminService {

    private final ReservationRepository reservationRepository;
    private final OrderRepository orderRepository;
    private final AuthService authService;

    public AdminService(
        ReservationRepository reservationRepository,
        OrderRepository orderRepository,
        AuthService authService
    ) {
        this.reservationRepository = reservationRepository;
        this.orderRepository = orderRepository;
        this.authService = authService;
    }

    public List<ReservationResponse> getAllReservations() {
        return reservationRepository.findByTableRestaurantId(currentRestaurantId()).stream()
            .map(AdminService::toResponse)
            .toList();
    }

    public List<OrderResponse> getAllOrders() {
        return orderRepository.findByRestaurantIdOrderByOrderDateDesc(currentRestaurantId()).stream()
            .map(OrderMapper::toResponse)
            .toList();
    }

    /** Pedidos aun en curso (cocina, listos, empaque o reparto). */
    public List<OrderResponse> getActiveOrders() {
        return orderRepository.findByRestaurantIdOrderByOrderDateDesc(currentRestaurantId()).stream()
            .filter(o -> !o.getStatus().isFinal())
            .map(OrderMapper::toResponse)
            .toList();
    }

    private Long currentRestaurantId() {
        Long restaurantId = authService.getCurrentUserRestaurantId();
        if (restaurantId == null) {
            throw new ValidationBusinessException("User not associated with a restaurant");
        }
        return restaurantId;
    }

    private static ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(
            reservation.getId(),
            reservation.getTable().getId(),
            reservation.getCustomer().getId(),
            reservation.getCustomer().getName(),
            reservation.getReservationDate(),
            reservation.getNumberOfGuests(),
            reservation.getStatus().name(),
            reservation.getCreatedAt(),
            reservation.getSpecialRequests(),
            reservation.isConfirmed()
        );
    }
}
