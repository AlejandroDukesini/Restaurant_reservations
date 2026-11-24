package com.restaurant.reservations.service;

import com.restaurant.reservations.dto.OrderItemResponse;
import com.restaurant.reservations.dto.OrderResponse;
import com.restaurant.reservations.model.Order;
import com.restaurant.reservations.model.OrderItem;
import java.util.List;

/** Conversion unica de Order a su DTO (antes duplicada en varios servicios). */
public final class OrderMapper {

    private OrderMapper() {
    }

    public static OrderResponse toResponse(Order order) {
        return toResponse(order, order.getItems());
    }

    public static OrderResponse toResponse(Order order, List<OrderItem> items) {
        return new OrderResponse(
            order.getId(),
            order.getTable() != null ? order.getTable().getId() : null,
            order.getTable() != null ? order.getTable().getTableNumber() : null,
            order.getEmployee().getId(),
            order.getEmployee().getName(),
            order.getOrderDate(),
            order.getStatus().name(),
            order.getTotalAmount(),
            items.stream().map(OrderMapper::toItem).toList(),
            order.getNotes(),
            order.getChannel().name(),
            label(order),
            order.getSession() != null ? order.getSession().getId() : null,
            order.getPaymentStatus().name(),
            order.getEstimatedMinutes(),
            order.getPreparingAt(),
            order.getReadyAt(),
            order.getPackingAt(),
            order.getPackedAt(),
            order.getDriverAssignedAt(),
            order.getDispatchedAt(),
            order.getDeliveredAt(),
            order.getCancelledAt(),
            order.getCancelReason(),
            order.getCustomerName(),
            order.getCustomerPhone(),
            order.getDeliveryAddress(),
            order.getExternalRef(),
            order.getPlatform() != null ? order.getPlatform().getId() : null,
            order.getPlatform() != null ? order.getPlatform().getName() : null,
            order.getDriver() != null ? order.getDriver().getId() : null,
            order.getDriver() != null ? order.getDriver().getName() : null
        );
    }

    public static OrderItemResponse toItem(OrderItem item) {
        return new OrderItemResponse(
            item.getId(),
            item.getMenuItem() != null ? item.getMenuItem().getId() : null,
            item.getItemName(),
            item.getQuantity(),
            item.getPrice(),
            item.getStatus().name(),
            item.getNotes(),
            item.getCategoryName(),
            item.getPrepMinutes()
        );
    }

    /** "Terraza 2" para mesa; "Domicilio #15 · Rappi" para domicilio. */
    public static String label(Order order) {
        if (order.isDelivery()) {
            String base = "Domicilio #" + order.getId();
            return order.getPlatform() != null ? base + " · " + order.getPlatform().getName() : base;
        }
        if (order.getTableLabel() != null) {
            return order.getTableLabel();
        }
        return order.getTable() != null ? order.getTable().getDisplayName() : "Pedido #" + order.getId();
    }
}
