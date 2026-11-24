package com.restaurant.reservations.controller;

import com.restaurant.reservations.dto.OpsDtos.CancelRequest;
import com.restaurant.reservations.dto.OpsDtos.ChatGroupSummary;
import com.restaurant.reservations.dto.OpsDtos.ChatMessageRequest;
import com.restaurant.reservations.dto.OpsDtos.ChatMessageResponse;
import com.restaurant.reservations.dto.OpsDtos.KitchenOrder;
import com.restaurant.reservations.dto.OpsDtos.MetricsResponse;
import com.restaurant.reservations.dto.OpsDtos.NotificationResponse;
import com.restaurant.reservations.dto.OpsDtos.OpenSessionRequest;
import com.restaurant.reservations.dto.OpsDtos.OrderEventResponse;
import com.restaurant.reservations.dto.OpsDtos.SessionResponse;
import com.restaurant.reservations.dto.OpsDtos.TableBoardItem;
import com.restaurant.reservations.dto.OrderRequest;
import com.restaurant.reservations.dto.OrderResponse;
import com.restaurant.reservations.security.UserPrincipal;
import com.restaurant.reservations.service.ChatService;
import com.restaurant.reservations.service.DeviceService;
import com.restaurant.reservations.service.FloorService;
import com.restaurant.reservations.service.KitchenService;
import com.restaurant.reservations.service.MetricsService;
import com.restaurant.reservations.service.NotificationService;
import com.restaurant.reservations.service.OrderService;
import com.restaurant.reservations.service.RealtimeService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Operacion diaria: canal en tiempo real, sala, pedidos, cocina, mensajeria, notificaciones
 * y metricas. Las reglas finas (grupo, autor del pedido, permisos) se aplican en los servicios.
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE', 'COOK', 'DELIVERY')")
public class OperationsController {

    private final RealtimeService realtime;
    private final DeviceService devices;
    private final FloorService floor;
    private final OrderService orders;
    private final KitchenService kitchen;
    private final ChatService chat;
    private final NotificationService notifications;
    private final MetricsService metrics;

    public OperationsController(
        RealtimeService realtime,
        DeviceService devices,
        FloorService floor,
        OrderService orders,
        KitchenService kitchen,
        ChatService chat,
        NotificationService notifications,
        MetricsService metrics
    ) {
        this.realtime = realtime;
        this.devices = devices;
        this.floor = floor;
        this.orders = orders;
        this.kitchen = kitchen;
        this.chat = chat;
        this.notifications = notifications;
        this.metrics = metrics;
    }

    // ------------------------------------------------------------------ Tiempo real

    /** Canal SSE. El token va en la cabecera Authorization (fetch), nunca en la URL. */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(required = false) String device,
        @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent
    ) {
        devices.touch(principal.getId(), device, userAgent);
        return realtime.connect(principal);
    }

    // ------------------------------------------------------------------ Sala

    @GetMapping("/floor/tables")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public List<TableBoardItem> board() {
        return floor.board();
    }

    @GetMapping("/floor/tables/{tableId}/session")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public SessionResponse session(@PathVariable Long tableId) {
        return floor.currentSession(tableId);
    }

    @PostMapping("/floor/tables/{tableId}/session")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public SessionResponse open(@PathVariable Long tableId, @Valid @RequestBody(required = false) OpenSessionRequest request) {
        return floor.open(tableId, request);
    }

    @PostMapping("/floor/tables/{tableId}/attention")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public SessionResponse attention(@PathVariable Long tableId) {
        return floor.requestAttention(tableId);
    }

    @DeleteMapping("/floor/tables/{tableId}/attention")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public SessionResponse clearAttention(@PathVariable Long tableId) {
        return floor.clearAttention(tableId);
    }

    @PostMapping("/floor/tables/{tableId}/bill")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public SessionResponse bill(@PathVariable Long tableId) {
        return floor.requestBill(tableId);
    }

    @PostMapping("/floor/tables/{tableId}/close")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public SessionResponse close(@PathVariable Long tableId) {
        return floor.close(tableId);
    }

    // ------------------------------------------------------------------ Pedidos

    /** Idempotency-Key (opcional): repetir la peticion devuelve el mismo pedido, no uno nuevo. */
    @PostMapping("/orders")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public OrderResponse create(
        @Valid @RequestBody OrderRequest request,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return orders.createOrder(request, idempotencyKey);
    }

    @GetMapping("/orders/{id}")
    public OrderResponse order(@PathVariable Long id) {
        return orders.getOrderById(id);
    }

    @GetMapping("/orders/{id}/history")
    public List<OrderEventResponse> history(@PathVariable Long id) {
        return orders.history(id);
    }

    @PostMapping("/orders/{id}/deliver")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public OrderResponse deliver(@PathVariable Long id) {
        return orders.deliverToTable(id);
    }

    @PostMapping("/orders/{id}/cancel")
    public OrderResponse cancel(@PathVariable Long id, @Valid @RequestBody(required = false) CancelRequest request) {
        return orders.cancel(id, request != null ? request.reason() : null);
    }

    // ------------------------------------------------------------------ Cocina

    @GetMapping("/kitchen/board")
    public List<KitchenOrder> kitchenBoard() {
        return kitchen.board();
    }

    @PutMapping("/kitchen/items/{id}/status")
    public ResponseEntity<Void> itemStatus(@PathVariable Long id, @RequestParam String status) {
        kitchen.updateItemStatus(id, status);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/kitchen/orders/{id}/start")
    public KitchenOrder start(@PathVariable Long id) {
        return kitchen.start(id);
    }

    @PostMapping("/kitchen/orders/{id}/ready")
    public KitchenOrder ready(@PathVariable Long id) {
        return kitchen.ready(id);
    }

    @PostMapping("/kitchen/orders/{id}/call-waiter")
    public ResponseEntity<Void> callWaiter(@PathVariable Long id) {
        kitchen.callWaiter(id);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ Mensajeria

    @GetMapping("/chat/groups")
    public List<ChatGroupSummary> chatGroups() {
        return chat.summaries();
    }

    @GetMapping("/chat/groups/{groupId}/messages")
    public List<ChatMessageResponse> messages(
        @PathVariable Long groupId,
        @RequestParam(required = false) Long before,
        @RequestParam(required = false) Long after
    ) {
        return chat.messages(groupId, before, after);
    }

    @PostMapping("/chat/groups/{groupId}/messages")
    public ChatMessageResponse post(@PathVariable Long groupId, @Valid @RequestBody ChatMessageRequest request) {
        return chat.post(groupId, request);
    }

    @PostMapping("/chat/groups/{groupId}/read")
    public ResponseEntity<Void> read(@PathVariable Long groupId) {
        chat.markRead(groupId);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ Notificaciones

    @GetMapping("/notifications")
    public List<NotificationResponse> notifications(@RequestParam(defaultValue = "30") int limit) {
        return notifications.mine(limit);
    }

    @GetMapping("/notifications/unread")
    public Map<String, Long> unread() {
        return notifications.unreadCount();
    }

    @PostMapping("/notifications/read")
    public ResponseEntity<Void> markRead(@RequestParam(required = false) Long id) {
        notifications.markRead(id);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ Metricas

    @GetMapping("/metrics")
    public MetricsResponse metrics(
        @RequestParam(defaultValue = "today") String period,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return metrics.compute(period, from, to);
    }
}
