package com.restaurant.reservations.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Ultimo mensaje leido por un usuario en un grupo (para contar no leidos). */
@Entity
@Table(name = "chat_reads")
public class ChatRead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private long lastReadId;

    protected ChatRead() {
    }

    public ChatRead(Long groupId, Long userId) {
        this.groupId = groupId;
        this.userId = userId;
    }

    public Long getGroupId() { return groupId; }
    public Long getUserId() { return userId; }
    public long getLastReadId() { return lastReadId; }
    public void setLastReadId(long lastReadId) { this.lastReadId = lastReadId; }
}
