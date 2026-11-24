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

@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @ManyToOne
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false, length = 1000)
    private String body;

    /** Referencia operativa opcional: TABLE u ORDER. */
    @Column(length = 20)
    private String refType;

    private Long refId;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected ChatMessage() {
    }

    public ChatMessage(Long restaurantId, Long groupId, User sender, String body, String refType, Long refId) {
        this.restaurantId = restaurantId;
        this.groupId = groupId;
        this.sender = sender;
        this.body = body;
        this.refType = refType;
        this.refId = refId;
    }

    public Long getId() { return id; }
    public Long getRestaurantId() { return restaurantId; }
    public Long getGroupId() { return groupId; }
    public User getSender() { return sender; }
    public String getBody() { return body; }
    public String getRefType() { return refType; }
    public Long getRefId() { return refId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
