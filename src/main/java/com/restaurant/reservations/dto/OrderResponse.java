package com.restaurant.reservations.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Pedido. Los diez primeros campos son el contrato original (los usa la app Android);
 * los nuevos van detras. tableId/tableNumber son null en domicilios.
 */
public record OrderResponse(
    long id,
    Long tableId,
    Integer tableNumber,
    long employeeId,
    String employeeName,
    LocalDateTime orderDate,
    String status,
    double totalAmount,
    List<OrderItemResponse> items,
    String notes,
    // --- Plataforma operativa ---
    String channel,
    String tableLabel,
    Long sessionId,
    String paymentStatus,
    Integer estimatedMinutes,
    LocalDateTime preparingAt,
    LocalDateTime readyAt,
    LocalDateTime packingAt,
    LocalDateTime packedAt,
    LocalDateTime driverAssignedAt,
    LocalDateTime dispatchedAt,
    LocalDateTime deliveredAt,
    LocalDateTime cancelledAt,
    String cancelReason,
    String customerName,
    String customerPhone,
    String deliveryAddress,
    String externalRef,
    Long platformId,
    String platformName,
    Long driverId,
    String driverName
) {
}
