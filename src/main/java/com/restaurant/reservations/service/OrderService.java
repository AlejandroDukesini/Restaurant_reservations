package com.restaurant.reservations.service;

import com.restaurant.reservations.dto.OrderItemRequest;
import com.restaurant.reservations.dto.OrderItemResponse;
import com.restaurant.reservations.dto.OrderRequest;
import com.restaurant.reservations.dto.OrderResponse;
import com.restaurant.reservations.exception.ResourceNotFoundException;
import com.restaurant.reservations.exception.ValidationBusinessException;
import com.restaurant.reservations.model.MenuItem;
import com.restaurant.reservations.model.Order;
import com.restaurant.reservations.model.OrderItem;
import com.restaurant.reservations.model.OrderStatus;
import com.restaurant.reservations.model.RestaurantTable;
import com.restaurant.reservations.model.Role;
import com.restaurant.reservations.model.User;
import com.restaurant.reservations.repository.MenuItemRepository;
import com.restaurant.reservations.repository.OrderItemRepository;
import com.restaurant.reservations.repository.OrderRepository;
import com.restaurant.reservations.repository.TableRepository;
import com.restaurant.reservations.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final int MAX_ITEMS_PER_ORDER = 100;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final TableRepository tableRepository;
    private final UserRepository userRepository;
    private final MenuItemRepository menuItemRepository;
    private final AuthService authService;

    public OrderService(
        OrderRepository orderRepository,
        OrderItemRepository orderItemRepository,
        TableRepository tableRepository,
        UserRepository userRepository,
        MenuItemRepository menuItemRepository,
        AuthService authService
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.tableRepository = tableRepository;
        this.userRepository = userRepository;
        this.menuItemRepository = menuItemRepository;
        this.authService = authService;
    }

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        Long employeeId = authService.getCurrentUserId();
        Long restaurantId = currentRestaurantId();

        RestaurantTable table = tableRepository.findById(request.tableId())
            .orElseThrow(() -> new ResourceNotFoundException("Table not found"));

        // La mesa debe ser del restaurante del mesero: sin esto se podian crear
        // pedidos sobre las mesas de otro restaurante (cross-tenant).
        requireSameRestaurant(table.getRestaurant().getId(), restaurantId, "Table not found");

        User employee = userRepository.findById(employeeId)
            .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        if (request.items().isEmpty()) {
            throw new IllegalArgumentException("Order must have at least one item");
        }
        if (request.items().size() > MAX_ITEMS_PER_ORDER) {
            throw new IllegalArgumentException("Order cannot have more than " + MAX_ITEMS_PER_ORDER + " items");
        }

        // Los platos son de menú (no personalizables): nombre y precio se toman del MenuItem.
        List<OrderItem> orderItems = new ArrayList<>();
        for (OrderItemRequest itemRequest : request.items()) {
            MenuItem menuItem = menuItemRepository.findById(itemRequest.menuItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found"));
            // Un plato de la carta de otro restaurante no puede entrar en este pedido.
            requireSameRestaurant(menuItem.getRestaurant().getId(), restaurantId, "Menu item not found");
            orderItems.add(new OrderItem(
                menuItem,
                menuItem.getName(),
                itemRequest.quantity(),
                menuItem.getPrice(),
                itemRequest.notes()
            ));
        }

        double totalAmount = 0.0;
        for (OrderItem item : orderItems) {
            totalAmount += item.getPrice() * item.getQuantity();
        }

        Order savedOrder = orderRepository.save(new Order(table, employee, totalAmount, request.notes()));

        // El pedido se guarda primero para que cada plato apunte a un id ya asignado.
        orderItems.forEach(item -> item.setOrder(savedOrder));
        List<OrderItem> savedOrderItems = orderItemRepository.saveAll(orderItems);

        return toResponse(savedOrder, savedOrderItems);
    }

    public List<OrderResponse> getMyOrders() {
        Long employeeId = authService.getCurrentUserId();
        return orderRepository.findByEmployeeId(employeeId).stream()
            .map(OrderService::toResponse)
            .toList();
    }

    public List<OrderResponse> getOrdersByTable(Long tableId) {
        Long restaurantId = currentRestaurantId();
        RestaurantTable table = tableRepository.findById(tableId)
            .orElseThrow(() -> new ResourceNotFoundException("Table not found"));
        requireSameRestaurant(table.getRestaurant().getId(), restaurantId, "Table not found");
        return orderRepository.findByTableId(tableId).stream()
            .map(OrderService::toResponse)
            .toList();
    }

    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        // Sin este control, un mesero podia leer los pedidos de cualquier otro
        // restaurante iterando el id (IDOR).
        requireSameRestaurant(order.getTable().getRestaurant().getId(), currentRestaurantId(), "Order not found");
        return toResponse(order);
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long id, String status) {
        Order order = orderRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        requireCanModify(order);

        OrderStatus newStatus;
        try {
            newStatus = OrderStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ValidationBusinessException("Invalid order status");
        }

        order.setStatus(newStatus);
        return toResponse(orderRepository.save(order));
    }

    @Transactional
    public void deleteOrder(Long id) {
        Order order = orderRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        requireCanModify(order);

        log.info(
            "Pedido {} eliminado por userId={} role={}",
            id, authService.getCurrentUserId(), authService.getCurrentUserRole()
        );
        orderRepository.delete(order);
    }

    /**
     * Modificar/borrar un pedido: su autor, o un ADMIN del mismo restaurante.
     * El chequeo de tenant es obligatorio incluso para ADMIN, porque el rol ADMIN
     * lo tiene el administrador de cada restaurante, no un superusuario global.
     */
    private void requireCanModify(Order order) {
        Long restaurantId = currentRestaurantId();
        requireSameRestaurant(order.getTable().getRestaurant().getId(), restaurantId, "Order not found");

        Long employeeId = authService.getCurrentUserId();
        if (!Objects.equals(order.getEmployee().getId(), employeeId) && authService.getCurrentUserRole() != Role.ADMIN) {
            log.warn("Acceso denegado al pedido {}: userId={}", order.getId(), employeeId);
            throw new ResourceNotFoundException("Order not found");
        }
    }

    private Long currentRestaurantId() {
        Long restaurantId = authService.getCurrentUserRestaurantId();
        if (restaurantId == null) {
            throw new ValidationBusinessException("User not associated with a restaurant");
        }
        return restaurantId;
    }

    private void requireSameRestaurant(Long ownerRestaurantId, Long callerRestaurantId, String message) {
        if (!Objects.equals(ownerRestaurantId, callerRestaurantId)) {
            log.warn(
                "Acceso cross-tenant denegado: userId={} (restaurante {}) pidio recurso del restaurante {}",
                authService.getCurrentUserId(), callerRestaurantId, ownerRestaurantId
            );
            // 404 en vez de 403: no confirma la existencia de recursos ajenos.
            throw new ResourceNotFoundException(message);
        }
    }

    private static OrderResponse toResponse(Order order) {
        return toResponse(order, order.getItems());
    }

    private static OrderResponse toResponse(Order order, List<OrderItem> items) {
        return new OrderResponse(
            order.getId(),
            order.getTable().getId(),
            order.getTable().getTableNumber(),
            order.getEmployee().getId(),
            order.getEmployee().getName(),
            order.getOrderDate(),
            order.getStatus().name(),
            order.getTotalAmount(),
            items.stream()
                .map(item -> new OrderItemResponse(
                    item.getId(),
                    item.getMenuItem() != null ? item.getMenuItem().getId() : null,
                    item.getItemName(),
                    item.getQuantity(),
                    item.getPrice(),
                    item.getStatus().name(),
                    item.getNotes()
                ))
                .toList(),
            order.getNotes()
        );
    }
}
