package com.restaurant.reservations.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.reservations.model.Role;
import com.restaurant.reservations.security.UserPrincipal;
import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Canal de sincronizacion entre dispositivos (Server-Sent Events).
 *
 * Diseno: la base de datos es la unica fuente de verdad. Los eventos son SENALES
 * ("cambiaron los pedidos", "mensaje nuevo en el grupo 4") sin datos de negocio; al recibir
 * una senal el cliente vuelve a pedir el estado por la API REST, que aplica todos los
 * controles de acceso. Si se pierde un evento (corte de red), al reconectar el cliente
 * recarga todo, asi que no hay estado desactualizado ni eventos "perdidos" que importen.
 *
 * Los eventos se emiten despues del COMMIT: un cliente nunca recarga antes de que el cambio
 * sea visible en la base.
 *
 * Limitacion: el registro de conexiones vive en memoria del proceso. Con varias instancias
 * del backend haria falta un bus compartido (p. ej. LISTEN/NOTIFY de PostgreSQL).
 */
@Service
public class RealtimeService {

    private static final Logger log = LoggerFactory.getLogger(RealtimeService.class);

    public static final String TOPIC_TABLES = "tables";
    public static final String TOPIC_ORDERS = "orders";
    public static final String TOPIC_DELIVERIES = "deliveries";
    public static final String TOPIC_MENU = "menu";
    public static final String TOPIC_CHAT = "chat";
    public static final String TOPIC_NOTIFICATIONS = "notifications";
    public static final String TOPIC_BRANDING = "branding";
    public static final String TOPIC_GROUPS = "groups";

    private record Client(long id, Long userId, Long restaurantId, Role role, SseEmitter emitter) {
    }

    private final List<Client> clients = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();
    private final ObjectMapper objectMapper;
    private final long timeoutMs;

    public RealtimeService(ObjectMapper objectMapper, @Value("${app.realtime.timeout-ms:1800000}") long timeoutMs) {
        this.objectMapper = objectMapper;
        this.timeoutMs = timeoutMs;
    }

    public SseEmitter connect(UserPrincipal principal) {
        SseEmitter emitter = new SseEmitter(timeoutMs);
        Client client = new Client(sequence.incrementAndGet(), principal.getId(), principal.getRestaurantId(),
            principal.getRole(), emitter);
        clients.add(client);
        Runnable remove = () -> clients.remove(client);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());
        // Primer evento: confirma la conexion; el cliente lo usa para resincronizar.
        send(client, Map.of("topic", "hello", "at", Instant.now().toString()));
        return emitter;
    }

    public int connectedClients() {
        return clients.size();
    }

    /** Senal para todo el personal del restaurante. */
    public void broadcast(Long restaurantId, String topic) {
        publish(restaurantId, topic, null, null);
    }

    /** Senal solo para ciertos usuarios (y siempre para los gerentes del restaurante). */
    public void toUsers(Long restaurantId, String topic, Long refId, Collection<Long> userIds) {
        publish(restaurantId, topic, refId, Set.copyOf(userIds));
    }

    private void publish(Long restaurantId, String topic, Long refId, Set<Long> userIds) {
        Runnable dispatch = () -> dispatch(restaurantId, topic, refId, userIds);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch.run();
                }
            });
        } else {
            dispatch.run();
        }
    }

    private void dispatch(Long restaurantId, String topic, Long refId, Set<Long> userIds) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("topic", topic);
        if (refId != null) {
            payload.put("refId", refId);
        }
        for (Client client : clients) {
            if (!restaurantId.equals(client.restaurantId())) {
                continue;
            }
            boolean targeted = userIds == null || userIds.contains(client.userId()) || client.role() == Role.ADMIN;
            if (targeted) {
                send(client, payload);
            }
        }
    }

    // Comentario SSE cada 20 s: mantiene viva la conexion a traves de proxies (Railway corta
    // conexiones inactivas) y detecta clientes caidos para liberarlos.
    @Scheduled(fixedRateString = "${app.realtime.heartbeat-ms:20000}")
    public void heartbeat() {
        for (Client client : clients) {
            try {
                client.emitter().send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException ex) {
                clients.remove(client);
            }
        }
    }

    private void send(Client client, Map<String, Object> payload) {
        try {
            client.emitter().send(SseEmitter.event().name("ops").data(objectMapper.writeValueAsString(payload)));
        } catch (JsonProcessingException ex) {
            log.error("No se pudo serializar el evento", ex);
        } catch (IOException | IllegalStateException ex) {
            clients.remove(client);
        }
    }
}
