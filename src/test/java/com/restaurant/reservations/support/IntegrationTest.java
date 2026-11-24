package com.restaurant.reservations.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.reservations.model.User;
import com.restaurant.reservations.repository.MenuItemRepository;
import com.restaurant.reservations.repository.OrderItemRepository;
import com.restaurant.reservations.repository.OrderRepository;
import com.restaurant.reservations.repository.ReservationRepository;
import com.restaurant.reservations.repository.RestaurantRepository;
import com.restaurant.reservations.repository.TableRepository;
import com.restaurant.reservations.repository.UserRepository;
import com.restaurant.reservations.repository.ZoneRepository;
import com.restaurant.reservations.security.JwtTokenProvider;
import com.restaurant.reservations.security.UserPrincipal;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Base de las pruebas de integracion: contexto completo de Spring (seguridad,
 * filtros, controladores, servicios y JPA reales) sobre H2 en memoria.
 *
 * No se usa @Transactional en las pruebas a proposito: asi cada peticion MockMvc
 * abre y confirma su propia transaccion como en produccion, y se detectan
 * problemas de carga lazy o de bloqueo que un rollback ocultaria. El aislamiento
 * se consigue vaciando las tablas despues de cada prueba.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected TestData data;
    @Autowired protected JwtTokenProvider tokenProvider;

    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private TableRepository tableRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RestaurantRepository restaurantRepository;

    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void cleanDatabase() {
        // Orden inverso a las claves foraneas. Las tablas de la plataforma operativa se
        // vacian con SQL directo (incluye la tabla de union de miembros de grupo).
        for (String table : new String[] {"notifications", "chat_reads", "chat_messages", "order_events",
            "user_devices"}) {
            jdbc.update("DELETE FROM " + table);
        }
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        jdbc.update("DELETE FROM table_sessions");
        jdbc.update("DELETE FROM delivery_drivers");
        jdbc.update("DELETE FROM delivery_platforms");
        reservationRepository.deleteAllInBatch();
        menuItemRepository.deleteAllInBatch();
        jdbc.update("DELETE FROM menu_categories");
        tableRepository.deleteAllInBatch();
        jdbc.update("DELETE FROM staff_group_members");
        jdbc.update("DELETE FROM staff_groups");
        zoneRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        jdbc.update("DELETE FROM media_files");
        restaurantRepository.deleteAllInBatch();
    }

    /** JWT real firmado por la aplicacion para un usuario ya persistido. */
    protected String tokenFor(User user) {
        UserPrincipal principal = new UserPrincipal(
            user.getId(),
            user.getEmail(),
            user.getPassword(),
            user.getRole(),
            user.getRestaurant() != null ? user.getRestaurant().getId() : null
        );
        return tokenProvider.generateToken(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    /** Adjunta el JWT del usuario: {@code get(...).with(authAs(user))}. */
    protected RequestPostProcessor authAs(User user) {
        String header = "Bearer " + tokenFor(user);
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, header);
            return request;
        };
    }

    /** Cuerpo JSON: un String se envia tal cual; cualquier otro objeto se serializa. */
    protected RequestPostProcessor json(Object body) {
        byte[] content = (body instanceof String text ? text : writeJson(body)).getBytes(StandardCharsets.UTF_8);
        return request -> {
            request.setContentType(MediaType.APPLICATION_JSON_VALUE);
            request.setContent(content);
            return request;
        };
    }

    protected JsonNode body(MvcResult result) throws JsonProcessingException, UnsupportedEncodingException {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** Mapa ordenado que admite valores null (Map.of no los admite): fields("k1", v1, "k2", v2...). */
    protected static Map<String, Object> fields(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private String writeJson(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
