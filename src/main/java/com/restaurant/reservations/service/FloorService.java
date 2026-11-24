package com.restaurant.reservations.service;

import com.restaurant.reservations.dto.OpsDtos.OpenSessionRequest;
import com.restaurant.reservations.dto.OpsDtos.SessionResponse;
import com.restaurant.reservations.dto.OpsDtos.TableBoardItem;
import com.restaurant.reservations.exception.ResourceNotFoundException;
import com.restaurant.reservations.exception.StateConflictException;
import com.restaurant.reservations.model.Order;
import com.restaurant.reservations.model.OrderStatus;
import com.restaurant.reservations.model.PaymentStatus;
import com.restaurant.reservations.model.Permission;
import com.restaurant.reservations.model.RestaurantTable;
import com.restaurant.reservations.model.StaffGroup;
import com.restaurant.reservations.model.TableSession;
import com.restaurant.reservations.model.TableSessionStatus;
import com.restaurant.reservations.model.TableStatus;
import com.restaurant.reservations.model.User;
import com.restaurant.reservations.repository.OrderEventRepository;
import com.restaurant.reservations.repository.OrderRepository;
import com.restaurant.reservations.repository.StaffGroupRepository;
import com.restaurant.reservations.repository.TableRepository;
import com.restaurant.reservations.repository.TableSessionRepository;
import com.restaurant.reservations.model.OrderEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operacion de sala: estado en vivo de las mesas y ciclo de atencion
 * (ocupar -> pedidos -> entrega -> cuenta -> cierre).
 *
 * Estado visible de la mesa (liveState), derivado y con una unica prioridad para que nunca
 * se muestren estados contradictorios:
 *   MAINTENANCE > ATTENTION > PAYMENT_PENDING > READY > PREPARING > SENT > DELIVERED > OCCUPIED > AVAILABLE
 *
 * Acceso por grupos: una mesa asignada a un grupo solo la operan los integrantes de ese grupo
 * (y el gerente). Las mesas sin grupo las opera cualquier mesero del restaurante.
 */
@Service
public class FloorService {

    private static final Logger log = LoggerFactory.getLogger(FloorService.class);

    private final TableRepository tableRepository;
    private final TableSessionRepository sessionRepository;
    private final OrderRepository orderRepository;
    private final OrderEventRepository eventRepository;
    private final StaffGroupRepository groupRepository;
    private final AccessService access;
    private final RealtimeService realtime;
    private final NotificationService notifications;

    public FloorService(
        TableRepository tableRepository,
        TableSessionRepository sessionRepository,
        OrderRepository orderRepository,
        OrderEventRepository eventRepository,
        StaffGroupRepository groupRepository,
        AccessService access,
        RealtimeService realtime,
        NotificationService notifications
    ) {
        this.tableRepository = tableRepository;
        this.sessionRepository = sessionRepository;
        this.orderRepository = orderRepository;
        this.eventRepository = eventRepository;
        this.groupRepository = groupRepository;
        this.access = access;
        this.realtime = realtime;
        this.notifications = notifications;
    }

    // ------------------------------------------------------------------ Lectura

    @Transactional(readOnly = true)
    public List<TableBoardItem> board() {
        Long restaurantId = access.restaurantId();
        Set<Long> myGroups = myGroupIds();
        boolean manager = access.isManager();

        List<RestaurantTable> tables = tableRepository.findByRestaurantIdAndActiveTrue(restaurantId).stream()
            .filter(t -> manager || canOperate(t, myGroups))
            .sorted(Comparator.comparingInt(RestaurantTable::getSortOrder)
                .thenComparingInt(RestaurantTable::getTableNumber))
            .toList();

        Map<Long, TableSession> sessionsByTable = sessionRepository
            .findOpenByRestaurant(restaurantId, TableSessionStatus.CLOSED).stream()
            .collect(Collectors.toMap(s -> s.getTable().getId(), s -> s, (a, b) -> a));
        Map<Long, List<Order>> ordersBySession = new HashMap<>();
        if (!sessionsByTable.isEmpty()) {
            List<Long> ids = sessionsByTable.values().stream().map(TableSession::getId).toList();
            for (Order order : orderRepository.findBySessionIds(ids)) {
                ordersBySession.computeIfAbsent(order.getSession().getId(), k -> new ArrayList<>()).add(order);
            }
        }

        List<TableBoardItem> result = new ArrayList<>();
        for (RestaurantTable table : tables) {
            TableSession session = sessionsByTable.get(table.getId());
            List<Order> orders = session != null ? ordersBySession.getOrDefault(session.getId(), List.of()) : List.of();
            result.add(toBoardItem(table, session, orders, manager || canOperate(table, myGroups)));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public SessionResponse currentSession(Long tableId) {
        RestaurantTable table = operableTable(tableId);
        TableSession session = sessionRepository.findOpenByTable(table.getId(), TableSessionStatus.CLOSED)
            .orElseThrow(() -> new ResourceNotFoundException("The table has no open session"));
        return toSessionResponse(session);
    }

    // ------------------------------------------------------------------ Ciclo de atencion

    @Transactional
    public SessionResponse open(Long tableId, OpenSessionRequest request) {
        RestaurantTable table = lockOperableTable(tableId);
        if (sessionRepository.findOpenByTable(table.getId(), TableSessionStatus.CLOSED).isPresent()) {
            throw new StateConflictException("The table is already occupied");
        }
        TableSession session = createSession(table, access.user(), request != null ? request.guests() : null);
        return toSessionResponse(session);
    }

    /**
     * Sesion abierta de la mesa o una nueva (al registrar el primer pedido sin ocupar antes).
     * La fila de la mesa queda bloqueada: dos meseros a la vez no crean dos sesiones.
     */
    @Transactional
    public TableSession openOrGet(Long tableId, User waiter) {
        RestaurantTable table = lockOperableTable(tableId);
        return sessionRepository.findOpenByTable(table.getId(), TableSessionStatus.CLOSED)
            .orElseGet(() -> createSession(table, waiter, null));
    }

    @Transactional
    public SessionResponse requestAttention(Long tableId) {
        TableSession session = openSessionOf(tableId);
        if (session.getAttentionRequestedAt() == null) {
            session.setAttentionRequestedAt(LocalDateTime.now());
            sessionRepository.save(session);
            Long waiterId = session.getWaiter() != null ? session.getWaiter().getId() : null;
            if (waiterId != null && !waiterId.equals(access.userId())) {
                notifications.notify(session.getRestaurantId(), List.of(waiterId), "TABLE_ATTENTION",
                    session.getTableLabel() + " requiere atencion", null, "TABLE", session.getTable().getId());
            }
            changed(session);
        }
        return toSessionResponse(session);
    }

    @Transactional
    public SessionResponse clearAttention(Long tableId) {
        TableSession session = openSessionOf(tableId);
        if (session.getAttentionRequestedAt() != null) {
            session.setAttentionRequestedAt(null);
            sessionRepository.save(session);
            changed(session);
        }
        return toSessionResponse(session);
    }

    /** El cliente pide la cuenta: la mesa pasa a "pendiente de pago". */
    @Transactional
    public SessionResponse requestBill(Long tableId) {
        TableSession session = openSessionOf(tableId);
        List<Order> orders = orderRepository.findBySessionIdOrderByIdAsc(session.getId());
        if (orders.stream().noneMatch(o -> o.getStatus() != OrderStatus.CANCELLED)) {
            throw new StateConflictException("The table has no orders to charge");
        }
        if (session.getStatus() == TableSessionStatus.OPEN) {
            session.setStatus(TableSessionStatus.BILL_REQUESTED);
            session.setBillRequestedAt(LocalDateTime.now());
            sessionRepository.save(session);
            changed(session);
        }
        return toSessionResponse(session);
    }

    /**
     * Cierre de la atencion: solo si no queda nada en curso (todo entregado o cancelado).
     * Los pedidos no cancelados quedan cobrados (PAID): es el ingreso definitivo de la mesa.
     */
    @Transactional
    public SessionResponse close(Long tableId) {
        TableSession session = openSessionOf(tableId);
        List<Order> orders = orderRepository.findBySessionIdOrderByIdAsc(session.getId());
        boolean pending = orders.stream().anyMatch(o -> !o.getStatus().isFinal());
        if (pending) {
            throw new StateConflictException("There are orders not yet delivered or cancelled");
        }
        LocalDateTime now = LocalDateTime.now();
        User actor = access.user();
        for (Order order : orders) {
            if (order.getStatus() == OrderStatus.DELIVERED && order.getPaymentStatus() != PaymentStatus.PAID) {
                order.setPaymentStatus(PaymentStatus.PAID);
                orderRepository.save(order);
                eventRepository.save(new OrderEvent(order, "PAID", "Cierre de mesa", actor));
            }
        }
        session.setStatus(TableSessionStatus.CLOSED);
        session.setClosedAt(now);
        session.setClosedBy(actor);
        session.setAttentionRequestedAt(null);
        sessionRepository.save(session);
        log.info("Mesa {} cerrada (sesion {}) por userId={}", tableId, session.getId(), actor.getId());
        changed(session);
        realtime.broadcast(session.getRestaurantId(), RealtimeService.TOPIC_ORDERS);
        return toSessionResponse(session);
    }

    // ------------------------------------------------------------------ Acceso

    /** Mesa del restaurante del usuario que el usuario puede operar (grupo). 404 si no. */
    public RestaurantTable operableTable(Long tableId) {
        RestaurantTable table = tableRepository.findById(tableId)
            .orElseThrow(() -> new ResourceNotFoundException("Table not found"));
        requireOperable(table);
        return table;
    }

    private RestaurantTable lockOperableTable(Long tableId) {
        RestaurantTable table = tableRepository.findByIdAndRestaurantIdForUpdate(tableId, access.restaurantId())
            .orElseThrow(() -> new ResourceNotFoundException("Table not found"));
        requireOperable(table);
        if (!table.isActive() || table.getStatus() == TableStatus.MAINTENANCE) {
            throw new StateConflictException("The table is not in service");
        }
        return table;
    }

    void requireOperable(RestaurantTable table) {
        access.requireSameRestaurant(table.getRestaurant().getId(), "Table not found");
        if (access.isManager() || access.can(Permission.MANAGE_TABLES)) {
            return;
        }
        if (!canOperate(table, myGroupIds())) {
            log.warn("Mesa {} de otro grupo denegada a userId={}", table.getId(), access.userId());
            throw new ResourceNotFoundException("Table not found");
        }
    }

    private static boolean canOperate(RestaurantTable table, Set<Long> myGroups) {
        StaffGroup group = table.getGroup();
        return group == null || !group.isActive() || myGroups.contains(group.getId());
    }

    private Set<Long> myGroupIds() {
        return groupRepository.findActiveByMember(access.userId()).stream()
            .map(StaffGroup::getId).collect(Collectors.toSet());
    }

    private TableSession openSessionOf(Long tableId) {
        RestaurantTable table = operableTable(tableId);
        return sessionRepository.findOpenByTable(table.getId(), TableSessionStatus.CLOSED)
            .orElseThrow(() -> new StateConflictException("The table has no open session"));
    }

    private TableSession createSession(RestaurantTable table, User waiter, Integer guests) {
        TableSession session = sessionRepository.saveAndFlush(new TableSession(table, waiter, guests));
        log.info("Mesa {} ocupada (sesion {}) por userId={}", table.getId(), session.getId(), waiter.getId());
        changed(session);
        return session;
    }

    private void changed(TableSession session) {
        realtime.broadcast(session.getRestaurantId(), RealtimeService.TOPIC_TABLES);
    }

    // ------------------------------------------------------------------ Mapeo

    SessionResponse toSessionResponse(TableSession session) {
        List<Order> orders = orderRepository.findBySessionIdOrderByIdAsc(session.getId());
        double total = orders.stream().filter(o -> o.getStatus() != OrderStatus.CANCELLED)
            .mapToDouble(Order::getTotalAmount).sum();
        return new SessionResponse(session.getId(), session.getTable().getId(), session.getTableLabel(),
            session.getStatus().name(),
            session.getWaiter() != null ? session.getWaiter().getId() : null,
            session.getWaiter() != null ? session.getWaiter().getName() : null,
            session.getGuests(), session.getOpenedAt(), session.getAttentionRequestedAt(),
            session.getBillRequestedAt(), session.getClosedAt(), round(total),
            orders.stream().map(OrderMapper::toResponse).toList());
    }

    private static TableBoardItem toBoardItem(RestaurantTable table, TableSession session, List<Order> orders,
                                              boolean canOperate) {
        List<Order> live = orders.stream().filter(o -> o.getStatus() != OrderStatus.CANCELLED).toList();
        int active = (int) live.stream().filter(o -> !o.getStatus().isFinal()).count();
        int ready = (int) live.stream().filter(o -> o.getStatus() == OrderStatus.READY).count();
        double total = live.stream().mapToDouble(Order::getTotalAmount).sum();
        LocalDateTime oldest = live.stream().filter(o -> !o.getStatus().isFinal()).map(Order::getOrderDate)
            .min(Comparator.naturalOrder()).orElse(null);
        return new TableBoardItem(
            table.getId(), table.getTableNumber(), table.getName(), table.getDisplayName(),
            table.getZone() != null ? table.getZone().getName() : null,
            table.getGroup() != null ? table.getGroup().getId() : null,
            table.getGroup() != null ? table.getGroup().getName() : null,
            table.getCapacity(), table.getFloor(), table.getGridX(), table.getGridY(),
            table.getStatus().name(), liveState(table, session, live), canOperate,
            session != null ? session.getId() : null,
            session != null && session.getWaiter() != null ? session.getWaiter().getId() : null,
            session != null && session.getWaiter() != null ? session.getWaiter().getName() : null,
            session != null ? session.getGuests() : null,
            session != null ? session.getOpenedAt() : null,
            session != null ? session.getAttentionRequestedAt() : null,
            session != null ? session.getBillRequestedAt() : null,
            active, ready, round(total), oldest);
    }

    static String liveState(RestaurantTable table, TableSession session, List<Order> liveOrders) {
        if (!table.isActive() || table.getStatus() == TableStatus.MAINTENANCE) {
            return "MAINTENANCE";
        }
        if (session == null) {
            return "AVAILABLE";
        }
        if (session.getAttentionRequestedAt() != null) {
            return "ATTENTION";
        }
        if (session.getStatus() == TableSessionStatus.BILL_REQUESTED) {
            return "PAYMENT_PENDING";
        }
        if (liveOrders.stream().anyMatch(o -> o.getStatus() == OrderStatus.READY)) {
            return "READY";
        }
        if (liveOrders.stream().anyMatch(o -> o.getStatus() == OrderStatus.PREPARING)) {
            return "PREPARING";
        }
        if (liveOrders.stream().anyMatch(o -> o.getStatus() == OrderStatus.PENDING)) {
            return "SENT";
        }
        if (!liveOrders.isEmpty() && liveOrders.stream().allMatch(o -> Objects.equals(o.getStatus(), OrderStatus.DELIVERED))) {
            return "DELIVERED";
        }
        return "OCCUPIED";
    }

    static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
