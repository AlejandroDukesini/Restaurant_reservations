package com.restaurant.reservations.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Grupo de trabajo definido por el gerente (Salon, Cocina, Sede Norte...). Tiene miembros,
 * canal de mensajeria propio e identidad visual opcional que reciben sus integrantes.
 */
@Entity
@Table(name = "staff_groups")
public class StaffGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 255)
    private String description;

    /** JSON de ThemeSettings; null = usa la personalizacion del restaurante. */
    @Column(length = 20000)
    private String theme;

    @Column(name = "logo_media_id", length = 36)
    private String logoMediaId;

    @Column(nullable = false)
    private boolean chatEnabled = true;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToMany
    @JoinTable(
        name = "staff_group_members",
        joinColumns = @JoinColumn(name = "group_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> members = new LinkedHashSet<>();

    protected StaffGroup() {
    }

    public StaffGroup(Long restaurantId, String name) {
        this.restaurantId = restaurantId;
        this.name = name;
    }

    public Long getId() { return id; }
    public Long getRestaurantId() { return restaurantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }
    public String getLogoMediaId() { return logoMediaId; }
    public void setLogoMediaId(String logoMediaId) { this.logoMediaId = logoMediaId; }
    public boolean isChatEnabled() { return chatEnabled; }
    public void setChatEnabled(boolean chatEnabled) { this.chatEnabled = chatEnabled; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Set<User> getMembers() { return members; }

    public boolean hasMember(Long userId) {
        return members.stream().anyMatch(u -> u.getId().equals(userId));
    }
}
