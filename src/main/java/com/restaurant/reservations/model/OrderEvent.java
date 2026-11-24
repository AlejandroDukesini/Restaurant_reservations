package com.restaurant.reservations.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Historial inmutable de un pedido: quien hizo que y cuando (hora del servidor). */
@Entity
@Table(name = "order_events")
public class OrderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(length = 255)
    private String detail;

    @ManyToOne
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected OrderEvent() {
    }

    public OrderEvent(Order order, String type, String detail, User actor) {
        this.order = order;
        this.type = type;
        this.detail = detail;
        this.actor = actor;
    }

    public Long getId() { return id; }
    public Order getOrder() { return order; }
    public String getType() { return type; }
    public String getDetail() { return detail; }
    public User getActor() { return actor; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
