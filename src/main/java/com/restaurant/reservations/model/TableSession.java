package com.restaurant.reservations.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;

/**
 * Una ocupacion de mesa: desde que el cliente se sienta hasta el cierre de la atencion.
 * Todos los pedidos de la mesa durante la ocupacion cuelgan de esta sesion, asi que los
 * pedidos de dos ocupaciones (o de dos mesas) nunca se mezclan.
 */
@Entity
@Table(name = "table_sessions")
public class TableSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @ManyToOne
    @JoinColumn(name = "table_id", nullable = false)
    private RestaurantTable table;

    /** Nombre de la mesa al abrir: el historico no cambia si luego se renombra. */
    @Column(nullable = false, length = 120)
    private String tableLabel;

    @ManyToOne
    @JoinColumn(name = "waiter_id")
    private User waiter;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TableSessionStatus status = TableSessionStatus.OPEN;

    private Integer guests;

    @Column(nullable = false)
    private LocalDateTime openedAt = LocalDateTime.now();

    private LocalDateTime attentionRequestedAt;
    private LocalDateTime billRequestedAt;
    private LocalDateTime closedAt;

    @ManyToOne
    @JoinColumn(name = "closed_by")
    private User closedBy;

    @Version
    private Long version;

    protected TableSession() {
    }

    public TableSession(RestaurantTable table, User waiter, Integer guests) {
        this.table = table;
        this.restaurantId = table.getRestaurant().getId();
        this.tableLabel = table.getDisplayName();
        this.waiter = waiter;
        this.guests = guests;
    }

    public Long getId() { return id; }
    public Long getRestaurantId() { return restaurantId; }
    public RestaurantTable getTable() { return table; }
    public String getTableLabel() { return tableLabel; }
    public User getWaiter() { return waiter; }
    public void setWaiter(User waiter) { this.waiter = waiter; }
    public TableSessionStatus getStatus() { return status; }
    public void setStatus(TableSessionStatus status) { this.status = status; }
    public Integer getGuests() { return guests; }
    public void setGuests(Integer guests) { this.guests = guests; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public LocalDateTime getAttentionRequestedAt() { return attentionRequestedAt; }
    public void setAttentionRequestedAt(LocalDateTime value) { this.attentionRequestedAt = value; }
    public LocalDateTime getBillRequestedAt() { return billRequestedAt; }
    public void setBillRequestedAt(LocalDateTime value) { this.billRequestedAt = value; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public User getClosedBy() { return closedBy; }
    public void setClosedBy(User closedBy) { this.closedBy = closedBy; }

    public boolean isOpen() {
        return status != TableSessionStatus.CLOSED;
    }
}
