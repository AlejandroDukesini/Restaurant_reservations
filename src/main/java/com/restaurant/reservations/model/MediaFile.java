package com.restaurant.reservations.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Imagen subida por el gerente (logo, portada, foto de plato).
 *
 * Se guarda en PostgreSQL (bytea) y no en disco: el sistema de archivos del contenedor de
 * Railway es efimero y se pierde en cada despliegue. El id es un UUID aleatorio, no secuencial.
 */
@Entity
@Table(name = "media_files")
public class MediaFile {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(nullable = false, length = 40)
    private String contentType;

    @Column(nullable = false)
    private int sizeBytes;

    private Integer width;
    private Integer height;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, length = 5_000_000)
    private byte[] data;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "created_by")
    private Long createdBy;

    protected MediaFile() {
    }

    public MediaFile(String id, Long restaurantId, String contentType, byte[] data, Integer width, Integer height, Long createdBy) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.contentType = contentType;
        this.data = data;
        this.sizeBytes = data.length;
        this.width = width;
        this.height = height;
        this.createdBy = createdBy;
    }

    public String getId() { return id; }
    public Long getRestaurantId() { return restaurantId; }
    public String getContentType() { return contentType; }
    public int getSizeBytes() { return sizeBytes; }
    public Integer getWidth() { return width; }
    public Integer getHeight() { return height; }
    public byte[] getData() { return data; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
