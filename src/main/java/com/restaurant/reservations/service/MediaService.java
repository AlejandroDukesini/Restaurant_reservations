package com.restaurant.reservations.service;

import com.restaurant.reservations.dto.OpsDtos.MediaResponse;
import com.restaurant.reservations.exception.ResourceNotFoundException;
import com.restaurant.reservations.exception.ValidationBusinessException;
import com.restaurant.reservations.model.MediaFile;
import com.restaurant.reservations.repository.MediaFileRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Subida y entrega de imagenes.
 *
 * Seguridad (OWASP File Upload):
 *  - Solo el gerente sube o borra (comprobado aqui, no solo en la ruta).
 *  - El tipo se decide por la FIRMA del archivo (bytes magicos), nunca por la extension ni
 *    por el Content-Type que declara el cliente. Solo PNG, JPEG y WebP: SVG queda fuera
 *    porque puede contener scripts (XSS almacenado).
 *  - Limite de peso y de dimensiones (evita "bombas" de descompresion).
 *  - Se sirven con el tipo detectado, nosniff y desde la propia API (mismo origen).
 */
@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);
    private static final int MAX_DIMENSION = 6000;

    private final MediaFileRepository mediaRepository;
    private final AccessService access;
    private final JdbcTemplate jdbc;
    private final long maxBytes;

    public MediaService(
        MediaFileRepository mediaRepository,
        AccessService access,
        JdbcTemplate jdbc,
        @Value("${app.media.max-bytes:3145728}") long maxBytes
    ) {
        this.mediaRepository = mediaRepository;
        this.access = access;
        this.jdbc = jdbc;
        this.maxBytes = maxBytes;
    }

    @Transactional
    public MediaResponse upload(MultipartFile file) {
        access.requireManager();
        return toResponse(store(file));
    }

    /**
     * Guarda una imagen validada para el usuario actual (foto de perfil). Cualquier miembro del
     * personal puede usarla, pero solo para si mismo: ProfileService la asigna a su cuenta.
     */
    @Transactional
    public MediaFile storeOwn(MultipartFile file) {
        return store(file);
    }

    private MediaFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationBusinessException("Empty file");
        }
        if (file.getSize() > maxBytes) {
            throw new ValidationBusinessException("File too large (max " + (maxBytes / 1024) + " KB)");
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException ex) {
            throw new ValidationBusinessException("Could not read file");
        }
        String type = detectType(data);
        if (type == null) {
            throw new ValidationBusinessException("Unsupported image format (PNG, JPEG or WebP only)");
        }
        int[] size = dimensions(data, type);
        if (size != null && (size[0] > MAX_DIMENSION || size[1] > MAX_DIMENSION || size[0] < 1 || size[1] < 1)) {
            throw new ValidationBusinessException("Image dimensions out of range");
        }
        MediaFile media = new MediaFile(UUID.randomUUID().toString(), access.restaurantId(), type, data,
            size != null ? size[0] : null, size != null ? size[1] : null, access.userId());
        mediaRepository.save(media);
        log.info("Imagen {} subida por userId={} ({} bytes, {})", media.getId(), access.userId(), data.length, type);
        return media;
    }

    /** Borra una imagen sin comprobar rol (uso interno: reemplazo de la foto de perfil propia). */
    @Transactional
    public void deleteInternal(String id) {
        if (id != null) {
            mediaRepository.findById(id).ifPresent(mediaRepository::delete);
        }
    }

    public MediaFile get(String id) {
        if (id == null || id.length() != 36) {
            throw new ResourceNotFoundException("Media not found");
        }
        return mediaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Media not found"));
    }

    /** Valida que una imagen exista y sea del restaurante actual (al asignarla como logo o foto). */
    public String requireOwned(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return mediaRepository.findByIdAndRestaurantId(id, access.restaurantId())
            .map(MediaFile::getId)
            .orElseThrow(() -> new ResourceNotFoundException("Media not found"));
    }

    @Transactional
    public void delete(String id) {
        access.requireManager();
        MediaFile media = mediaRepository.findByIdAndRestaurantId(id, access.restaurantId())
            .orElseThrow(() -> new ResourceNotFoundException("Media not found"));
        // Las FK de PostgreSQL ya ponen NULL (ON DELETE SET NULL); se hace explicito para
        // que el comportamiento no dependa del motor.
        jdbc.update("UPDATE restaurants SET logo_media_id = NULL WHERE logo_media_id = ?", id);
        jdbc.update("UPDATE restaurants SET cover_media_id = NULL WHERE cover_media_id = ?", id);
        jdbc.update("UPDATE staff_groups SET logo_media_id = NULL WHERE logo_media_id = ?", id);
        jdbc.update("UPDATE menu_items SET image_media_id = NULL WHERE image_media_id = ?", id);
        jdbc.update("UPDATE users SET avatar_media_id = NULL WHERE avatar_media_id = ?", id);
        mediaRepository.delete(media);
    }

    public static String url(String mediaId) {
        return mediaId == null ? null : "/api/media/" + mediaId;
    }

    private static MediaResponse toResponse(MediaFile media) {
        return new MediaResponse(media.getId(), url(media.getId()), media.getContentType(), media.getSizeBytes(),
            media.getWidth(), media.getHeight());
    }

    /** Tipo real segun la firma binaria; null si no es PNG/JPEG/WebP. */
    static String detectType(byte[] d) {
        if (d.length >= 8 && (d[0] & 0xff) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G'
            && d[4] == 0x0D && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A) {
            return "image/png";
        }
        if (d.length >= 3 && (d[0] & 0xff) == 0xFF && (d[1] & 0xff) == 0xD8 && (d[2] & 0xff) == 0xFF) {
            return "image/jpeg";
        }
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
            && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    /** Lee solo la cabecera (no decodifica la imagen completa). */
    private static int[] dimensions(byte[] data, String type) {
        if ("image/webp".equals(type)) {
            return webpDimensions(data);
        }
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new ValidationBusinessException("Corrupt image");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                return new int[] {reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (IOException ex) {
            throw new ValidationBusinessException("Corrupt image");
        }
    }

    private static int[] webpDimensions(byte[] d) {
        if (d.length < 30) {
            throw new ValidationBusinessException("Corrupt image");
        }
        String chunk = new String(d, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
        switch (chunk) {
            case "VP8X":
                return new int[] {1 + u24(d, 24), 1 + u24(d, 27)};
            case "VP8L": {
                int b0 = d[21] & 0xff;
                int b1 = d[22] & 0xff;
                int b2 = d[23] & 0xff;
                int b3 = d[24] & 0xff;
                int w = 1 + (((b1 & 0x3F) << 8) | b0);
                int h = 1 + (((b3 & 0x0F) << 10) | (b2 << 2) | ((b1 & 0xC0) >> 6));
                return new int[] {w, h};
            }
            case "VP8 ":
                return new int[] {u16(d, 26) & 0x3FFF, u16(d, 28) & 0x3FFF};
            default:
                throw new ValidationBusinessException("Corrupt image");
        }
    }

    private static int u24(byte[] d, int i) {
        return (d[i] & 0xff) | ((d[i + 1] & 0xff) << 8) | ((d[i + 2] & 0xff) << 16);
    }

    private static int u16(byte[] d, int i) {
        return (d[i] & 0xff) | ((d[i + 1] & 0xff) << 8);
    }
}
