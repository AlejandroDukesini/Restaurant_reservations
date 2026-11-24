package com.restaurant.reservations.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Pedido de mesa (DINE_IN, ligado a una sesion de mesa) o de domicilio (DELIVERY, sin mesa).
 *
 * Todas las marcas de tiempo las pone el servidor al cambiar de estado; el cliente nunca
 * envia horas, asi que las metricas no se pueden alterar desde el frontend. @Version evita
 * el doble procesamiento cuando dos dispositivos cambian el mismo pedido a la vez.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private OrderChannel channel = OrderChannel.DINE_IN;

    @ManyToOne
    @JoinColumn(name = "table_id")
    private RestaurantTable table;

    @ManyToOne
    @JoinColumn(name = "session_id")
    private TableSession session;

    /** Nombre de la mesa al crear el pedido (historico estable). */
    @Column(length = 120)
    private String tableLabel;

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private User employee;

    /** Hora de registro y envio a cocina. */
    @Column(nullable = false)
    private LocalDateTime orderDate = LocalDateTime.now();

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Column(nullable = false)
    private double totalAmount = 0.0;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    @Column(length = 1000)
    private String notes;

    /** Mayor tiempo estimado de los platos (minutos); referencia para detectar retrasos. */
    private Integer estimatedMinutes;

    private LocalDateTime preparingAt;
    private LocalDateTime readyAt;
    private LocalDateTime packingAt;
    private LocalDateTime packedAt;
    private LocalDateTime driverAssignedAt;
    private LocalDateTime dispatchedAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime cancelledAt;

    @Column(length = 255)
    private String cancelReason;

    // --- Domicilio ---
    @Column(length = 120)
    private String customerName;

    @Column(length = 40)
    private String customerPhone;

    @Column(length = 255)
    private String deliveryAddress;

    @Column(length = 60)
    private String externalRef;

    @ManyToOne
    @JoinColumn(name = "platform_id")
    private DeliveryPlatform platform;

    @ManyToOne
    @JoinColumn(name = "driver_id")
    private DeliveryDriver driver;

    @Version
    private Long version;

    protected Order() {
    }

    /** Pedido de mesa. */
    public Order(TableSession session, User employee, String notes) {
        this.channel = OrderChannel.DINE_IN;
        this.session = session;
        this.table = session.getTable();
        this.tableLabel = session.getTable().getDisplayName();
        this.restaurantId = session.getRestaurantId();
        this.employee = employee;
        this.notes = notes;
    }

    /** Pedido a domicilio. */
    public Order(Long restaurantId, User employee, String notes) {
        this.channel = OrderChannel.DELIVERY;
        this.restaurantId = restaurantId;
        this.employee = employee;
        this.notes = notes;
    }

    public Long getId() { return id; }
    public Long getRestaurantId() { return restaurantId; }
    public OrderChannel getChannel() { return channel; }
    public RestaurantTable getTable() { return table; }
    public TableSession getSession() { return session; }
    public String getTableLabel() { return tableLabel; }
    public User getEmployee() { return employee; }
    public LocalDateTime getOrderDate() { return orderDate; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }
    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }
    public List<OrderItem> getItems() { return items; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Integer getEstimatedMinutes() { return estimatedMinutes; }
    public void setEstimatedMinutes(Integer estimatedMinutes) { this.estimatedMinutes = estimatedMinutes; }
    public LocalDateTime getPreparingAt() { return preparingAt; }
    public void setPreparingAt(LocalDateTime value) { this.preparingAt = value; }
    public LocalDateTime getReadyAt() { return readyAt; }
    public void setReadyAt(LocalDateTime value) { this.readyAt = value; }
    public LocalDateTime getPackingAt() { return packingAt; }
    public void setPackingAt(LocalDateTime value) { this.packingAt = value; }
    public LocalDateTime getPackedAt() { return packedAt; }
    public void setPackedAt(LocalDateTime value) { this.packedAt = value; }
    public LocalDateTime getDriverAssignedAt() { return driverAssignedAt; }
    public void setDriverAssignedAt(LocalDateTime value) { this.driverAssignedAt = value; }
    public LocalDateTime getDispatchedAt() { return dispatchedAt; }
    public void setDispatchedAt(LocalDateTime value) { this.dispatchedAt = value; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime value) { this.deliveredAt = value; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime value) { this.cancelledAt = value; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
    public String getExternalRef() { return externalRef; }
    public void setExternalRef(String externalRef) { this.externalRef = externalRef; }
    public DeliveryPlatform getPlatform() { return platform; }
    public void setPlatform(DeliveryPlatform platform) { this.platform = platform; }
    public DeliveryDriver getDriver() { return driver; }
    public void setDriver(DeliveryDriver driver) { this.driver = driver; }

    public boolean isDelivery() {
        return channel == OrderChannel.DELIVERY;
    }
}
