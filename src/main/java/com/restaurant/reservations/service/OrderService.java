package com.restaurant.reservations.service;

import com.restaurant.reservations.dto.OpsDtos.OrderEventResponse;
import com.restaurant.reservations.dto.OrderItemRequest;
import com.restaurant.reservations.dto.OrderRequest;
import com.restaurant.reservations.dto.OrderResponse;
import com.restaurant.reservations.exception.ResourceNotFoundException;
import com.restaurant.reservations.exception.StateConflictException;
import com.restaurant.reservations.exception.ValidationBusinessException;
import com.restaurant.reservations.model.MenuItem;
import com.restaurant.reservations.model.Order;
import com.restaurant.reservations.model.OrderEvent;
import com.restaurant.reservations.model.OrderItem;
import com.restaurant.reservations.model.OrderItemStatus;
import com.restaurant.reservations.model.OrderStatus;
import com.restaurant.reservations.model.PaymentStatus;
import com.restaurant.reservations.model.Permission;
import com.restaurant.reservations.model.Role;
import com.restaurant.reservations.model.TableSession;
import com.restaurant.reservations.model.TableSessionStatus;
import com.restaurant.reservations.model.User;
import com.restaurant.reservations.repository.MenuItemRepository;
import com.restaurant.reservations.repository.OrderEventRepository;
import com.restaurant.reservations.repository.OrderItemRepository;
import com.restaurant.reservations.repository.OrderRepository;
import com.restaurant.reservations.repository.TableSessionRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pedidos y su flujo: registro -> cocina -> listo -> entrega (mesa) / empaque -> reparto (domicilio).
 *
 * Reglas:
 *  - Toda transicion la valida el servidor contra el estado actual (no se puede marcar como
 *    entregado un pedido que cocina no ha terminado, ni "reabrir" uno entregado).
 *  - Las horas las pone el servidor; el cliente nunca las envia.
 *  - Cada cambio queda en order_events (quien, que, cuando).
 *  - Nombre, precio, categoria y tiempo estimado de cada plato se copian del menu al pedir:
 *    el cliente no puede fijar precios y el historico no cambia si el menu cambia.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final int MAX_ITEMS_PER_ORDER = 100;
    private static final int IDEMPOTENCY_CACHE = 2000;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderEventRepository eventRepository;
    private final MenuItemRepository menuItemRepository;
    private final TableSessionRepository sessionRepository;
    private final FloorService floorService;
    private final AccessService access;
    private final RealtimeService realtime;
    private final NotificationService notifications;

    /** Clave de idempotencia -> pedido creado. Evita duplicados por doble toque o reintento de red. */
    private final Map<String, Long> recentKeys = java.util.Collections.synchronizedMap(
        new LinkedHashMap<>(256, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                return size() > IDEMPOTENCY_CACHE;
            }
        });

    public OrderService(
        OrderRepository orderRepository,
        OrderItemRepository orderItemRepository,
        OrderEventRepository eventRepository,
        MenuItemRepository menuItemRepository,
        TableSessionRepository sessionRepository,
        FloorService floorService,
        AccessService access,
        RealtimeService realtime,
        NotificationService notifications
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.eventRepository = eventRepository;
        this.menuItemRepository = menuItemRepository;
        this.sessionRepository = sessionRepository;
        this.floorService = floorService;
        this.access = access;
        this.realtime = realtime;
        this.notifications = notifications;
    }

    // ------------------------------------------------------------------ Creacion

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        return createOrder(request, null);
    }

    @Transactional
    public OrderResponse createOrder(OrderRequest request, String idempotencyKey) {
        String key = idempotencyKey(idempotencyKey);
        if (key != null) {
            Long existing = recentKeys.get(key);
            if (existing != null) {
                return orderRepository.findById(existing).map(OrderMapper::toResponse)
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
            }
        }
        User employee = access.user();
        // Abre la atencion si la mesa estaba libre; bloquea la fila de la mesa (sin carreras).
        TableSession session = floorService.openOrGet(request.tableId(), employee);
        if (session.getStatus() == TableSessionStatus.BILL_REQUESTED) {
            // Un pedido nuevo despues de pedir la cuenta vuelve a dejar la mesa en atencion.
            session.setStatus(TableSessionStatus.OPEN);
            session.setBillRequestedAt(null);
            sessionRepository.save(session);
        }
        Order order = new Order(session, employee, blankToNull(request.notes()));
        Order saved = persistWithItems(order, request.items(), employee);
        if (key != null) {
            recentKeys.put(key, saved.getId());
        }
        return OrderMapper.toResponse(saved);
    }

    /** Valida y copia los platos del menu, calcula total y estimado, guarda y registra el evento. */
    Order persistWithItems(Order order, List<OrderItemRequest> requests, User actor) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("Order must have at least one item");
        }
        if (requests.size() > MAX_ITEMS_PER_ORDER) {
            throw new IllegalArgumentException("Order cannot have more than " + MAX_ITEMS_PER_ORDER + " items");
        }
        Long restaurantId = order.getRestaurantId();
        List<OrderItem> items = new ArrayList<>();
        double total = 0;
        int estimated = 0;
        for (OrderItemRequest itemRequest : requests) {
            MenuItem menuItem = menuItemRepository.findByIdAndRestaurantId(itemRequest.menuItemId(), restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found"));
            if (!menuItem.isActive()) {
                throw new ResourceNotFoundException("Menu item not found");
            }
            if (!menuItem.isAvailable()) {
                throw new StateConflictException("Producto agotado: " + menuItem.getName());
            }
            OrderItem item = new OrderItem(menuItem, menuItem.getName(), itemRequest.quantity(), menuItem.getPrice(),
                blankToNull(itemRequest.notes()));
            item.setCategoryName(menuItem.getCategory().getName());
            item.setPrepMinutes(menuItem.getPrepMinutes());
            items.add(item);
            total += menuItem.getPrice() * itemRequest.quantity();
            if (menuItem.getPrepMinutes() != null) {
                estimated = Math.max(estimated, menuItem.getPrepMinutes());
            }
        }
        order.setTotalAmount(FloorService.round(total));
        order.setEstimatedMinutes(estimated > 0 ? estimated : null);
        Order saved = orderRepository.save(order);
        items.forEach(item -> item.setOrder(saved));
        saved.getItems().addAll(orderItemRepository.saveAll(items));
        record(saved, "CREATED", items.size() + " platos · enviado a cocina", actor);
        changed(saved);
        return saved;
    }

    // ------------------------------------------------------------------ Lectura

    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders() {
        return orderRepository.findByEmployeeId(access.userId()).stream()
            .filter(o -> o.getRestaurantId().equals(access.restaurantId()))
            .map(OrderMapper::toResponse).toList();
    }

    /**
     * Pedidos de la ATENCION ACTUAL de la mesa (no de clientes anteriores): asi los pedidos de
     * dos ocupaciones distintas nunca se mezclan en la vista del mesero.
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByTable(Long tableId) {
        floorService.operableTable(tableId);
        return sessionRepository.findOpenByTable(tableId, TableSessionStatus.CLOSED)
            .map(s -> orderRepository.findBySessionIdOrderByIdAsc(s.getId()).stream()
                .map(OrderMapper::toResponse).toList())
            .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        return OrderMapper.toResponse(readable(id));
    }

    @Transactional(readOnly = true)
    public List<OrderEventResponse> history(Long id) {
        Order order = readable(id);
        return eventRepository.findByOrderIdOrderByIdAsc(order.getId()).stream()
            .map(e -> new OrderEventResponse(e.getId(), e.getType(), e.getDetail(),
                e.getActor() != null ? e.getActor().getName() : null, e.getCreatedAt()))
            .toList();
    }

    /** Pedido legible por el usuario: mismo restaurante; un domiciliario solo los suyos. */
    Order readable(Long id) {
        Order order = orderRepository.findByIdAndRestaurantId(id, access.restaurantId())
            .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (access.role() == Role.DELIVERY && !isAssignedDriver(order, access.userId())) {
            throw new ResourceNotFoundException("Order not found");
        }
        if (order.getTable() != null && access.hasRole(Role.EMPLOYEE)) {
            floorService.requireOperable(order.getTable());
        }
        return order;
    }

    // ------------------------------------------------------------------ Transiciones (sala)

    /** El mesero entrega en la mesa un pedido que cocina marco como listo. */
    @Transactional
    public OrderResponse deliverToTable(Long id) {
        Order order = lockedOrder(id);
        if (order.isDelivery()) {
            throw new StateConflictException("Delivery orders are delivered by the driver");
        }
        requireCanServe(order);
        if (order.getStatus() != OrderStatus.READY) {
            throw new StateConflictException("Only a ready order can be delivered");
        }
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(now);
        orderRepository.save(order);
        record(order, "DELIVERED", "Entregado en " + OrderMapper.label(order), access.user());
        changed(order);
        return OrderMapper.toResponse(order);
    }

    /**
     * Cancelacion: el autor mientras cocina no la haya empezado; despues, solo el gerente o
     * quien tenga CANCEL_ORDERS. Un pedido entregado o ya cancelado no se puede cancelar.
     */
    @Transactional
    public OrderResponse cancel(Long id, String reason) {
        Order order = lockedOrder(id);
        if (order.getStatus().isFinal()) {
            throw new StateConflictException("The order is already " + order.getStatus().name().toLowerCase(Locale.ROOT));
        }
        boolean author = Objects.equals(order.getEmployee().getId(), access.userId());
        boolean privileged = access.can(Permission.CANCEL_ORDERS);
        if (!privileged) {
            if (!author) {
                log.warn("Cancelacion denegada del pedido {} a userId={}", id, access.userId());
                throw new ResourceNotFoundException("Order not found");
            }
            if (order.getStatus() != OrderStatus.PENDING) {
                throw new StateConflictException("The kitchen already started this order; ask a manager to cancel it");
            }
            if (order.getTable() != null) {
                floorService.requireOperable(order.getTable());
            }
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        order.setCancelReason(blankToNull(reason));
        orderRepository.save(order);
        User actor = access.user();
        record(order, "CANCELLED", reason != null && !reason.isBlank() ? reason.strip() : "Cancelado", actor);
        if (!author) {
            notifications.notify(order.getRestaurantId(), List.of(order.getEmployee().getId()), "ORDER_CANCELLED",
                "Cancelacion autorizada: " + OrderMapper.label(order), reason, "ORDER", order.getId());
        }
        changed(order);
        return OrderMapper.toResponse(order);
    }

    /** Endpoint historico PUT /status: solo admite las transiciones que el usuario puede hacer. */
    @Transactional
    public OrderResponse updateOrderStatus(Long id, String status) {
        String value = status == null ? "" : status.toUpperCase(Locale.ROOT);
        return switch (value) {
            case "DELIVERED", "COMPLETED" -> deliverToTable(id);
            case "CANCELLED" -> cancel(id, null);
            case "PENDING", "IN_PROGRESS", "PREPARING", "READY" -> {
                requireCanServe(lockedOrder(id));
                throw new StateConflictException("Kitchen progress is set from the kitchen view");
            }
            default -> throw new ValidationBusinessException("Invalid order status");
        };
    }

    /**
     * DELETE historico. Ya no borra fisicamente (se perderia la trazabilidad y las metricas):
     * equivale a cancelar con las mismas reglas.
     */
    @Transactional
    public void deleteOrder(Long id) {
        cancel(id, "Eliminado por el mesero");
    }

    // ------------------------------------------------------------------ Progreso de cocina

    /**
     * Recalcula el estado del pedido a partir de sus platos tras un cambio en cocina:
     * algun plato empezado -> PREPARING; todos listos -> READY (y aviso al mesero).
     */
    void applyKitchenProgress(Order order, User actor) {
        if (!order.getStatus().isInKitchen()) {
            return;
        }
        List<OrderItem> items = order.getItems();
        if (items.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        boolean started = items.stream().anyMatch(i -> i.getStatus() != OrderItemStatus.PENDING);
        boolean allReady = items.stream().allMatch(i -> i.getStatus() == OrderItemStatus.READY);
        if (started && order.getPreparingAt() == null) {
            order.setPreparingAt(now);
            if (order.getStatus() == OrderStatus.PENDING) {
                order.setStatus(OrderStatus.PREPARING);
                record(order, "PREPARING", "Cocina inicio la preparacion", actor);
            }
        }
        if (allReady) {
            order.setStatus(OrderStatus.READY);
            order.setReadyAt(now);
            record(order, "READY", "Pedido listo en cocina", actor);
            Set<Long> recipients = new LinkedHashSet<>();
            recipients.add(order.getEmployee().getId());
            if (order.getSession() != null && order.getSession().getWaiter() != null) {
                recipients.add(order.getSession().getWaiter().getId());
            }
            notifications.notify(order.getRestaurantId(), recipients, "ORDER_READY",
                "Pedido listo · " + OrderMapper.label(order),
                order.isDelivery() ? "Listo para empacar" : "Recoger en cocina y entregar", "ORDER", order.getId());
        }
        orderRepository.save(order);
        changed(order);
    }

    // ------------------------------------------------------------------ Utilidades

    /**
     * Servir/entregar un pedido de mesa: su autor, el mesero a cargo de la mesa, el gerente o
     * un integrante del grupo responsable de la mesa. Cualquier otro recibe 404.
     */
    private void requireCanServe(Order order) {
        if (order.getTable() == null) {
            throw new ResourceNotFoundException("Order not found");
        }
        floorService.requireOperable(order.getTable());
        Long me = access.userId();
        boolean author = Objects.equals(order.getEmployee().getId(), me);
        boolean tableWaiter = order.getSession() != null && order.getSession().getWaiter() != null
            && Objects.equals(order.getSession().getWaiter().getId(), me);
        boolean groupMember = order.getTable().getGroup() != null && order.getTable().getGroup().hasMember(me);
        if (!(author || tableWaiter || groupMember || access.isManager())) {
            log.warn("Pedido {} denegado a userId={}", order.getId(), me);
            throw new ResourceNotFoundException("Order not found");
        }
    }

    Order lockedOrder(Long id) {
        // @Version del pedido: si otro dispositivo lo cambio entre lectura y escritura, el
        // segundo recibe 409 y debe recargar (sin doble procesamiento).
        return orderRepository.findByIdAndRestaurantId(id, access.restaurantId())
            .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    void record(Order order, String type, String detail, User actor) {
        eventRepository.save(new OrderEvent(order, type, detail, actor));
    }

    void changed(Order order) {
        realtime.broadcast(order.getRestaurantId(), RealtimeService.TOPIC_ORDERS);
        realtime.broadcast(order.getRestaurantId(),
            order.isDelivery() ? RealtimeService.TOPIC_DELIVERIES : RealtimeService.TOPIC_TABLES);
    }

    static boolean isAssignedDriver(Order order, Long userId) {
        return order.getDriver() != null && order.getDriver().getUser() != null
            && order.getDriver().getUser().getId().equals(userId);
    }

    void markPaid(Order order) {
        order.setPaymentStatus(PaymentStatus.PAID);
    }

    private String idempotencyKey(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 80) {
            return null;
        }
        return access.userId() + ":" + raw.strip();
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
