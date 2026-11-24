package com.restaurant.reservations.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Dispositivo desde el que un usuario se conecto al canal en tiempo real. */
@Entity
@Table(name = "user_devices")
public class UserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String deviceKey;

    @Column(length = 120)
    private String label;

    @Column(length = 255)
    private String userAgent;

    @Column(nullable = false)
    private LocalDateTime firstSeenAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime lastSeenAt = LocalDateTime.now();

    protected UserDevice() {
    }

    public UserDevice(Long userId, String deviceKey) {
        this.userId = userId;
        this.deviceKey = deviceKey;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getDeviceKey() { return deviceKey; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public LocalDateTime getFirstSeenAt() { return firstSeenAt; }
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(LocalDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }
}
