package com.restaurant.reservations.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Domiciliario. Si tiene cuenta (rol DELIVERY), queda vinculado a su usuario. */
@Entity
@Table(name = "delivery_drivers")
public class DeliveryDriver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 40)
    private String phone;

    @OneToOne
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected DeliveryDriver() {
    }

    public DeliveryDriver(Long restaurantId, String name) {
        this.restaurantId = restaurantId;
        this.name = name;
    }

    public Long getId() { return id; }
    public Long getRestaurantId() { return restaurantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
