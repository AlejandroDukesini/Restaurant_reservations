package com.restaurant.reservations.service;

import com.restaurant.reservations.dto.CookQueueItem;
import com.restaurant.reservations.dto.OpsDtos.KitchenItem;
import com.restaurant.reservations.dto.OpsDtos.KitchenOrder;
import com.restaurant.reservations.exception.ResourceNotFoundException;
import com.restaurant.reservations.exception.StateConflictException;
import com.restaurant.reservations.model.MenuItem;
import com.restaurant.reservations.model.Order;
import com.restaurant.reservations.model.OrderItem;
import com.restaurant.reservations.model.OrderItemStatus;
import com.restaurant.reservations.model.OrderStatus;
import com.restaurant.reservations.model.Permission;
import com.restaurant.reservations.model.Role;
import com.restaurant.reservations.model.User;
import com.restaurant.reservations.repository.OrderItemRepository;
import com.restaurant.reservations.repository.OrderRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vista y acciones de cocina. Acceso: cocineros, gerente o empleados con KITCHEN_ACCESS.
 *
 * El progreso de un plato solo avanza (PENDING -> PREPARING -> READY): no se puede "deshacer"
 * un listo para manipular los tiempos medidos.
 */
@Service
public class KitchenService {

    private static final List<OrderStatus> QUEUE_STATUSES = List.of(OrderStatus.PENDING, OrderStatus.PREPARING);
    private static final List<OrderStatus> BOARD_STATUSES =
        List.of(OrderStatus.PENDING, OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.PACKING);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderService orderService;
    private final AccessService access;
    private final NotificationService notifications;

    public KitchenService(
        OrderRepository orderRepository,
        OrderItemRepository orderItemRepository,
        OrderService orderService,
        AccessService access,
        NotificationService notifications
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderService = orderService;
        this.access = access;
        this.notifications = notifications;
    }

    /** Pedidos activos de cocina agrupados (mesa o domicilio), del mas antiguo al mas nuevo. */
    @Transactional(readOnly = true)
    public List<KitchenOrder> board() {
        requireKitchen();
        return orderRepository.findActiveByRestaurant(access.restaurantId(), BOARD_STATUSES).stream()
            .map(KitchenService::toKitchenOrder)
            .toList();
    }

    /** Cola historica plato a plato (contrato que usa la app Android). */
    @Transactional(readOnly = true)
    public List<CookQueueItem> getQueue() {
        requireKitchen();
        List<CookQueueItem> queue = new ArrayList<>();
        for (Order order : orderRepository.findActiveByRestaurant(access.restaurantId(), QUEUE_STATUSES)) {
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
        requireKitchen();
        OrderItem item = orderItemRepository.findById(orderItemId)
            .orElseThrow(() -> new ResourceNotFoundException("Order item not found"));
        Order order = item.getOrder();
        if (order == null || !order.getRestaurantId().equals(access.restaurantId())) {
            throw new ResourceNotFoundException("Order item not found");
        }
        OrderItemStatus next = parse(status);
        advance(order, item, next);
        orderService.applyKitchenProgress(order, access.user());
        return toQueueItem(order, item);
    }

    /** Empezar todo el pedido. */
    @Transactional
    public KitchenOrder start(Long orderId) {
        requireKitchen();
        Order order = orderService.lockedOrder(orderId);
        requireInKitchen(order);
        for (OrderItem item : order.getItems()) {
            if (item.getStatus() == OrderItemStatus.PENDING) {
                item.setStatus(OrderItemStatus.PREPARING);
                orderItemRepository.save(item);
            }
        }
        orderService.applyKitchenProgress(order, access.user());
        return toKitchenOrder(order);
    }

    /** Marcar todo el pedido como listo (notifica al mesero responsable). */
    @Transactional
    public KitchenOrder ready(Long orderId) {
        requireKitchen();
        Order order = orderService.lockedOrder(orderId);
        requireInKitchen(order);
        for (OrderItem item : order.getItems()) {
            if (item.getStatus() != OrderItemStatus.READY) {
                item.setStatus(OrderItemStatus.READY);
                orderItemRepository.save(item);
            }
        }
        orderService.applyKitchenProgress(order, access.user());
        return toKitchenOrder(order);
    }

    /** "Mesero solicitado": aviso solo al mesero que registro el pedido (y al de la mesa). */
    @Transactional
    public void callWaiter(Long orderId) {
        requireKitchen();
        Order order = orderService.lockedOrder(orderId);
        if (order.getStatus().isFinal()) {
            throw new StateConflictException("The order is already closed");
        }
        List<Long> recipients = new ArrayList<>();
        recipients.add(order.getEmployee().getId());
        if (order.getSession() != null && order.getSession().getWaiter() != null) {
            recipients.add(order.getSession().getWaiter().getId());
        }
        User cook = access.user();
        notifications.notify(order.getRestaurantId(), recipients, "WAITER_CALLED",
            "Cocina solicita mesero · " + OrderMapper.label(order), "Llamado por " + cook.getName(), "ORDER",
            order.getId());
        orderService.record(order, "WAITER_CALLED", "Cocina solicito al mesero", cook);
    }

    private void advance(Order order, OrderItem item, OrderItemStatus next) {
        requireInKitchen(order);
        OrderItemStatus current = item.getStatus();
        if (current == next) {
            return;
        }
        boolean forward = (current == OrderItemStatus.PENDING) || (current == OrderItemStatus.PREPARING
            && next == OrderItemStatus.READY);
        if (!forward || next == OrderItemStatus.PENDING) {
            throw new StateConflictException("Kitchen progress can only move forward");
        }
        item.setStatus(next);
        orderItemRepository.save(item);
    }

    private static void requireInKitchen(Order order) {
        if (!order.getStatus().isInKitchen()) {
            throw new StateConflictException("The order is no longer in the kitchen");
        }
    }

    void requireKitchen() {
        if (access.hasRole(Role.ADMIN, Role.COOK) || access.can(Permission.KITCHEN_ACCESS)) {
            return;
        }
        throw new AccessDeniedException("Access denied");
    }

    private static OrderItemStatus parse(String status) {
        try {
            return OrderItemStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new IllegalArgumentException("Invalid item status: " + status);
        }
    }

    static KitchenOrder toKitchenOrder(Order order) {
        List<KitchenItem> items = order.getItems().stream().map(item -> {
            MenuItem menu = item.getMenuItem();
            return new KitchenItem(item.getId(), item.getItemName(), item.getQuantity(), item.getStatus().name(),
                item.getNotes(), item.getCategoryName(), item.getPrepMinutes(),
                menu != null ? menu.getProtein() : null,
                menu != null ? menu.getCondiments() : null,
                menu != null ? menu.getIngredients() : null,
                menu != null ? menu.getPreparationNotes() : null);
        }).toList();
        return new KitchenOrder(order.getId(), order.getChannel().name(), OrderMapper.label(order),
            order.getEmployee().getName(), order.getOrderDate(), order.getStatus().name(), order.getEstimatedMinutes(),
            order.getPreparingAt(), order.getReadyAt(), order.getNotes(), items);
    }

    private static CookQueueItem toQueueItem(Order order, OrderItem item) {
        MenuItem menuItem = item.getMenuItem();
        boolean table = order.getTable() != null;
        return new CookQueueItem(
            item.getId(),
            order.getId(),
            table ? order.getTable().getId() : 0L,
            table ? order.getTable().getTableNumber() : 0,
            OrderMapper.label(order),
            order.getEmployee().getName(),
            order.getOrderDate(),
            item.getItemName(),
            item.getQuantity(),
            item.getStatus().name(),
            item.getNotes(),
            item.getCategoryName() != null ? item.getCategoryName()
                : menuItem != null ? menuItem.getCategory().getName() : null,
            menuItem != null ? menuItem.getProtein() : null,
            menuItem != null ? menuItem.getCondiments() : null,
            menuItem != null ? menuItem.getIngredients() : null,
            menuItem != null ? menuItem.getPreparationNotes() : null
        );
    }
}
