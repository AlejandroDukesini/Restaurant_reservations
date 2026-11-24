package com.restaurant.reservations.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Notificacion operativa dirigida a UN usuario (p. ej. el mesero de un pedido listo). */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 255)
    private String body;

    @Column(length = 20)
    private String refType;

    private Long refId;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime readAt;

    protected Notification() {
    }

    public Notification(Long restaurantId, Long recipientId, String type, String title, String body, String refType, Long refId) {
        this.restaurantId = restaurantId;
        this.recipientId = recipientId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.refType = refType;
        this.refId = refId;
    }

    public Long getId() { return id; }
    public Long getRestaurantId() { return restaurantId; }
    public Long getRecipientId() { return recipientId; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getRefType() { return refType; }
    public Long getRefId() { return refId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
}
