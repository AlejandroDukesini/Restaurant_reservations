package com.restaurant.reservations.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTOs de la plataforma operativa (grupos, mensajeria, mesas, cocina, domicilios, menu,
 * personalizacion y metricas). Agrupados como records anidados para no dispersar decenas
 * de archivos de una sola linea.
 */
public final class OpsDtos {

    private OpsDtos() {
    }

    // ------------------------------------------------------------------ Notificaciones
    public record NotificationResponse(
        long id, String type, String title, String body, String refType, Long refId,
        LocalDateTime createdAt, boolean read
    ) {
    }

    // ------------------------------------------------------------------ Archivos
    public record MediaResponse(String id, String url, String contentType, int sizeBytes, Integer width, Integer height) {
    }

    // ------------------------------------------------------------------ Personalizacion
    public record BrandingResponse(
        String restaurantName,
        ThemeSettings theme,
        String logoUrl,
        String coverUrl,
        String source,
        Long groupId,
        String groupName
    ) {
    }

    public record RestaurantBrandingRequest(
        @NotBlank @Size(max = 120) String restaurantName,
        @Valid ThemeSettings theme,
        @Size(max = 36) String logoMediaId,
        @Size(max = 36) String coverMediaId
    ) {
    }

    public record GroupBrandingRequest(
        @Valid ThemeSettings theme,
        @Size(max = 36) String logoMediaId
    ) {
    }

    // ------------------------------------------------------------------ Perfil propio
    public record ProfileRequest(
        @NotBlank @Size(min = 2, max = 120) String name,
        @Valid ThemeSettings theme
    ) {
    }

    /** theme = tema personal (null si hereda); inheritedTheme = el del grupo/restaurante. */
    public record ProfileResponse(long id, String name, String email, String role, String avatarUrl,
                                  ThemeSettings theme, ThemeSettings inheritedTheme, String inheritedFrom) {
    }

    // ------------------------------------------------------------------ Grupos
    public record GroupRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 255) String description,
        Boolean chatEnabled
    ) {
    }

    public record GroupMembersRequest(@NotNull @Size(max = 500) List<Long> userIds) {
    }

    public record MemberSummary(long id, String name, String email, String role, boolean active) {
    }

    public record GroupResponse(
        long id,
        String name,
        String description,
        boolean chatEnabled,
        List<MemberSummary> members,
        ThemeSettings theme,
        String logoUrl,
        String logoMediaId,
        LocalDateTime createdAt
    ) {
    }

    public record DeviceResponse(long userId, String userName, String label, LocalDateTime firstSeenAt,
                                 LocalDateTime lastSeenAt) {
    }

    public record GroupActivity(long groupId, long messages, LocalDateTime lastMessageAt, List<DeviceResponse> devices) {
    }

    // ------------------------------------------------------------------ Mensajeria
    public record ChatGroupSummary(long groupId, String name, boolean chatEnabled, long unread, String lastMessage,
                                   String lastSender, LocalDateTime lastAt, int members) {
    }

    public record ChatMessageRequest(
        @NotBlank @Size(max = 1000) String body,
        @Pattern(regexp = "TABLE|ORDER") String refType,
        Long refId
    ) {
    }

    public record ChatMessageResponse(long id, long groupId, long senderId, String senderName, String senderRole,
                                      String body, String refType, Long refId, LocalDateTime createdAt, boolean mine) {
    }

    // ------------------------------------------------------------------ Mesas / sesiones
    public record TableBoardItem(
        long id,
        int tableNumber,
        String name,
        String displayName,
        String zoneName,
        Long groupId,
        String groupName,
        int capacity,
        int floor,
        int gridX,
        int gridY,
        String operationalStatus,
        String liveState,
        boolean canOperate,
        Long sessionId,
        Long waiterId,
        String waiterName,
        Integer guests,
        LocalDateTime openedAt,
        LocalDateTime attentionRequestedAt,
        LocalDateTime billRequestedAt,
        int activeOrders,
        int readyOrders,
        double sessionTotal,
        LocalDateTime oldestActiveOrderAt
    ) {
    }

    public record OpenSessionRequest(@Min(1) @Max(100) Integer guests) {
    }

    public record SessionResponse(
        long id,
        long tableId,
        String tableLabel,
        String status,
        Long waiterId,
        String waiterName,
        Integer guests,
        LocalDateTime openedAt,
        LocalDateTime attentionRequestedAt,
        LocalDateTime billRequestedAt,
        LocalDateTime closedAt,
        double total,
        List<OrderResponse> orders
    ) {
    }

    // ------------------------------------------------------------------ Pedidos
    public record CancelRequest(@Size(max = 255) String reason) {
    }

    public record OrderEventResponse(long id, String type, String detail, String actorName, LocalDateTime createdAt) {
    }

    public record DeliveryOrderRequest(
        @Valid @NotEmpty @Size(max = 100) List<OrderItemRequest> items,
        @Size(max = 1000) String notes,
        @Size(max = 120) String customerName,
        @Size(max = 40) String customerPhone,
        @Size(max = 255) String deliveryAddress,
        @Size(max = 60) String externalRef,
        Long platformId,
        Long driverId
    ) {
    }

    public record AssignDriverRequest(Long driverId) {
    }

    // ------------------------------------------------------------------ Cocina
    public record KitchenItem(
        long orderItemId, String itemName, int quantity, String status, String notes, String categoryName,
        Integer prepMinutes, String protein, String condiments, String ingredients, String preparationNotes
    ) {
    }

    public record KitchenOrder(
        long orderId, String channel, String label, String employeeName, LocalDateTime orderDate, String status,
        Integer estimatedMinutes, LocalDateTime preparingAt, LocalDateTime readyAt, String notes,
        List<KitchenItem> items
    ) {
    }

    // ------------------------------------------------------------------ Domicilios
    public record PlatformRequest(@NotBlank @Size(max = 60) String name, Boolean active) {
    }

    public record PlatformResponse(long id, String name, boolean active) {
    }

    public record DriverRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 40) String phone,
        Long userId,
        Boolean active
    ) {
    }

    public record DriverResponse(long id, String name, String phone, Long userId, String userEmail, boolean active,
                                 long activeDeliveries) {
    }

    // ------------------------------------------------------------------ Menu
    public record CategoryRequest(@NotBlank @Size(max = 80) String name, @Min(0) @Max(10000) Integer sortOrder) {
    }

    public record CategoryResponse(long id, String name, int sortOrder, long itemCount) {
    }

    public record AvailabilityRequest(@NotNull Boolean available) {
    }

    public record PriceRequest(@NotNull @PositiveOrZero @Max(100_000_000) Double price) {
    }

    // ------------------------------------------------------------------ Metricas
    /** Promedio y muestra de una metrica de tiempo (minutos). count = pedidos con dato real. */
    public record TimeStat(Double avgMinutes, Double maxMinutes, long count) {
    }

    public record NamedAmount(String name, long count, double amount) {
    }

    public record EmployeeStat(long employeeId, String name, long orders, Double avgReadyToDeliveredMinutes,
                               double revenue) {
    }

    public record ChannelTimes(String name, long orders, Double avgTotalMinutes, Double avgDeliveryMinutes) {
    }

    public record DailyPoint(String date, long orders, double revenue) {
    }

    public record MetricsResponse(
        LocalDateTime from,
        LocalDateTime to,
        Map<String, Object> summary,
        Map<String, TimeStat> dineInTimes,
        Map<String, TimeStat> deliveryTimes,
        List<NamedAmount> topProducts,
        List<NamedAmount> categories,
        List<NamedAmount> revenueByTable,
        List<NamedAmount> revenueByChannel,
        List<NamedAmount> revenueByPlatform,
        List<ChannelTimes> platformPerformance,
        List<ChannelTimes> driverPerformance,
        List<EmployeeStat> employees,
        List<DailyPoint> trend,
        Map<String, Object> previousPeriod,
        List<Map<String, Object>> bottlenecks
    ) {
    }
}
