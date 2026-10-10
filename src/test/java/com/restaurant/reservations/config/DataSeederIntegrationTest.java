package com.restaurant.reservations.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.restaurant.reservations.model.RestaurantTable;
import com.restaurant.reservations.model.Role;
import com.restaurant.reservations.model.User;
import com.restaurant.reservations.repository.MenuItemRepository;
import com.restaurant.reservations.repository.RestaurantRepository;
import com.restaurant.reservations.repository.TableRepository;
import com.restaurant.reservations.repository.UserRepository;
import com.restaurant.reservations.repository.ZoneRepository;
import com.restaurant.reservations.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

/** La siembra de demostracion: contenido esperado, idempotencia e interruptor SEED_ENABLED. */
class DataSeederIntegrationTest extends IntegrationTest {

    private static final String SEED_PASSWORD = "semilla-sintetica-2026";

    @Autowired private RestaurantRepository restaurantRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private TableRepository tableRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private DataSeeder seeder(boolean enabled) {
        return new DataSeeder(
            restaurantRepository, zoneRepository, tableRepository, userRepository, menuItemRepository,
            passwordEncoder, enabled, SEED_PASSWORD
        );
    }

    @Test
    @DisplayName("siembra un restaurante completo y es idempotente")
    void siembraUnRestauranteCompletoYEsIdempotente() {
        DataSeeder s = seeder(true);
        s.run(new DefaultApplicationArguments());
        s.run(new DefaultApplicationArguments()); // segunda ejecucion: no duplica

        assertEquals(1, restaurantRepository.count());
        assertEquals(5, zoneRepository.count());
        assertEquals(16, tableRepository.count());
        assertEquals(10, menuItemRepository.count());

        List<User> users = userRepository.findAll();
        assertEquals(5, users.size());
        assertEquals(
            Map.of(Role.ADMIN, 1L, Role.EMPLOYEE, 2L, Role.COOK, 2L),
            users.stream().collect(Collectors.groupingBy(User::getRole, Collectors.counting()))
        );
        // La contrasena configurada (SEED_PASSWORD) es la que queda, nunca en claro.
        assertTrue(users.stream().allMatch(u -> passwordEncoder.matches(SEED_PASSWORD, u.getPassword())));

        Map<Integer, Double> prices = tableRepository.findAll().stream()
            .collect(Collectors.toMap(RestaurantTable::getTableNumber, RestaurantTable::getPrice));
        assertEquals(45.0, prices.get(1)); // 2 plazas
        assertEquals(65.0, prices.get(3)); // 4 plazas
        assertEquals(95.0, prices.get(9)); // 8 plazas
    }

    @Test
    @DisplayName("con SEED_ENABLED=false no crea nada")
    void conSeedDesactivadoNoCreaNada() {
        seeder(false).run(new DefaultApplicationArguments());
        assertEquals(0, restaurantRepository.count());
        assertEquals(0, userRepository.count());
    }
}
