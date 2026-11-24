package com.restaurant.reservations.service;

import com.restaurant.reservations.dto.OpsDtos.NotificationResponse;
import com.restaurant.reservations.model.Notification;
import com.restaurant.reservations.repository.NotificationRepository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notificaciones operativas dirigidas: solo reciben aviso los usuarios relacionados con la
 * operacion (el mesero del pedido, el domiciliario asignado...), nunca todo el personal.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final RealtimeService realtime;
    private final AccessService access;

    public NotificationService(NotificationRepository notificationRepository, RealtimeService realtime, AccessService access) {
        this.notificationRepository = notificationRepository;
        this.realtime = realtime;
        this.access = access;
    }

    /** Crea una notificacion por destinatario (sin duplicados, sin nulos) y avisa por SSE. */
    @Transactional
    public void notify(Long restaurantId, Collection<Long> recipients, String type, String title, String body,
                       String refType, Long refId) {
        Set<Long> unique = new LinkedHashSet<>();
        recipients.stream().filter(Objects::nonNull).forEach(unique::add);
        if (unique.isEmpty()) {
            return;
        }
        for (Long recipient : unique) {
            notificationRepository.save(new Notification(restaurantId, recipient, type, truncate(title, 120),
                truncate(body, 255), refType, refId));
        }
        realtime.toUsers(restaurantId, RealtimeService.TOPIC_NOTIFICATIONS, refId, unique);
    }

    public List<NotificationResponse> mine(int limit) {
        int size = Math.max(1, Math.min(limit, 100));
        return notificationRepository.findByRecipientIdOrderByIdDesc(access.userId(), PageRequest.of(0, size)).stream()
            .map(n -> new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getRefType(),
                n.getRefId(), n.getCreatedAt(), n.getReadAt() != null))
            .toList();
    }

    public Map<String, Long> unreadCount() {
        return Map.of("unread", notificationRepository.countByRecipientIdAndReadAtIsNull(access.userId()));
    }

    /** Marca como leida una notificacion propia (o todas si id es null). Nunca las de otro usuario. */
    @Transactional
    public void markRead(Long id) {
        notificationRepository.markRead(access.userId(), id, LocalDateTime.now());
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
