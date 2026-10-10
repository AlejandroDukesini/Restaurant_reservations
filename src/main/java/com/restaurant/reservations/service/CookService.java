package com.restaurant.reservations.service;

import com.restaurant.reservations.dto.CookQueueItem;
import com.restaurant.reservations.model.MenuItem;
import com.restaurant.reservations.model.Order;
import com.restaurant.reservations.model.OrderItem;
import com.restaurant.reservations.model.OrderItemStatus;
import com.restaurant.reservations.model.OrderStatus;
import com.restaurant.reservations.repository.OrderItemRepository;
import com.restaurant.reservations.repository.OrderRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CookService {

    private static final List<OrderStatus> ACTIVE_ORDER_STATUSES = List.of(OrderStatus.PENDING, OrderStatus.IN_PROGRESS);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final AuthService authService;

    public CookService(OrderRepository orderRepository, OrderItemRepository orderItemRepository, AuthService authService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.authService = authService;
    }

    // Cola de cocina: todos los platos aún no listos de los pedidos activos del restaurante.
    public List<CookQueueItem> getQueue() {
        Long restaurantId = currentRestaurantId();
        List<CookQueueItem> queue = new ArrayList<>();
        for (Order order : orderRepository.findActiveByRestaurant(restaurantId, ACTIVE_ORDER_STATUSES)) {
            for (OrderItem item : order.getItems()) {
                if (item.getStatus() != OrderItemStatus.READY) {
                    queue.add(toQueueItem(order, item));
                }
            }
        }
        return queue;
    }

    @Transactional
    public CookQueueItem updateItemStatus(Long orderItemId, String status) {
        Long restaurantId = currentRestaurantId();
        OrderItem item = orderItemRepository.findById(orderItemId)
            .orElseThrow(() -> new IllegalArgumentException("Order item not found"));

        Order order = item.getOrder();
        if (order == null) {
            throw new IllegalArgumentException("Order item is not attached to an order");
        }

        if (!Objects.equals(order.getTable().getRestaurant().getId(), restaurantId)) {
            throw new IllegalArgumentException("Unauthorized access to order item");
        }

        OrderItemStatus newStatus;
        try {
            newStatus = OrderItemStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid item status: " + status);
        }

        item.setStatus(newStatus);
        OrderItem savedItem = orderItemRepository.save(item);
        syncOrderStatus(order.getId());

        return toQueueItem(order, savedItem);
    }

    // Recalcula el estado del pedido a partir de sus platos.
    @Transactional
    public void syncOrderStatus(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        List<OrderItem> items = order.getItems();
        if (items.isEmpty()) {
            return;
        }

        OrderStatus newStatus;
        if (items.stream().allMatch(i -> i.getStatus() == OrderItemStatus.READY)) {
            newStatus = OrderStatus.COMPLETED;
        } else if (items.stream().anyMatch(i -> i.getStatus() != OrderItemStatus.PENDING)) {
            newStatus = OrderStatus.IN_PROGRESS;
        } else {
            newStatus = OrderStatus.PENDING;
        }

        if (newStatus != order.getStatus() && order.getStatus() != OrderStatus.CANCELLED) {
            order.setStatus(newStatus);
            orderRepository.save(order);
        }
    }

    private Long currentRestaurantId() {
        Long restaurantId = authService.getCurrentUserRestaurantId();
        if (restaurantId == null) {
            throw new IllegalArgumentException("User not associated with a restaurant");
        }
        return restaurantId;
    }

    private static CookQueueItem toQueueItem(Order order, OrderItem item) {
        MenuItem menuItem = item.getMenuItem();
        return new CookQueueItem(
            item.getId(),
            order.getId(),
            order.getTable().getId(),
            order.getTable().getTableNumber(),
            order.getTable().getName(),
            order.getEmployee().getName(),
            order.getOrderDate(),
            item.getItemName(),
            item.getQuantity(),
            item.getStatus().name(),
            item.getNotes(),
            menuItem != null ? menuItem.getCategory().name() : null,
            menuItem != null ? menuItem.getProtein() : null,
            menuItem != null ? menuItem.getCondiments() : null,
            menuItem != null ? menuItem.getIngredients() : null,
            menuItem != null ? menuItem.getPreparationNotes() : null
        );
    }
}
