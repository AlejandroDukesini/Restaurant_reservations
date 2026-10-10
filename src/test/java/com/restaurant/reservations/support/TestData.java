package com.restaurant.reservations.support;

import com.restaurant.reservations.model.MenuCategory;
import com.restaurant.reservations.model.MenuItem;
import com.restaurant.reservations.model.Restaurant;
import com.restaurant.reservations.model.RestaurantTable;
import com.restaurant.reservations.model.Role;
import com.restaurant.reservations.model.TableStatus;
import com.restaurant.reservations.model.User;
import com.restaurant.reservations.model.Zone;
import com.restaurant.reservations.repository.MenuItemRepository;
import com.restaurant.reservations.repository.RestaurantRepository;
import com.restaurant.reservations.repository.TableRepository;
import com.restaurant.reservations.repository.UserRepository;
import com.restaurant.reservations.repository.ZoneRepository;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Fabrica de datos sinteticos para las pruebas de integracion. Todos los correos
 * usan el dominio reservado `example.test` y ningun dato corresponde a personas reales.
 */
@Component
public class TestData {

    /** Contrasena sintetica de las cuentas de prueba (cumple el minimo de 12). */
    public static final String PASSWORD = "Sintetica-Prueba-2026";

    private final RestaurantRepository restaurantRepository;
    private final ZoneRepository zoneRepository;
    private final TableRepository tableRepository;
    private final UserRepository userRepository;
    private final MenuItemRepository menuItemRepository;
    private final PasswordEncoder passwordEncoder;
    private final AtomicInteger sequence = new AtomicInteger();

    // BCrypt con coste 12 tarda cientos de ms: se calcula una sola vez por contexto.
    private String passwordHash;

    public TestData(
        RestaurantRepository restaurantRepository,
        ZoneRepository zoneRepository,
        TableRepository tableRepository,
        UserRepository userRepository,
        MenuItemRepository menuItemRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.restaurantRepository = restaurantRepository;
        this.zoneRepository = zoneRepository;
        this.tableRepository = tableRepository;
        this.userRepository = userRepository;
        this.menuItemRepository = menuItemRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public synchronized String passwordHash() {
        if (passwordHash == null) {
            passwordHash = passwordEncoder.encode(PASSWORD);
        }
        return passwordHash;
    }

    public Restaurant restaurant() {
        return restaurant("Restaurante " + sequence.incrementAndGet());
    }

    public Restaurant restaurant(String name) {
        String slug = name.toLowerCase(Locale.ROOT).replace(" ", "-").replaceAll("[^a-z0-9-]", "");
        Restaurant restaurant = new Restaurant(
            name,
            slug,
            "Restaurante de prueba",
            "Calle Falsa 123",
            "+00 000 000",
            "contacto@" + slug + ".example.test",
            4,
            16,
            1
        );
        restaurant.setWebsiteUrl("/" + slug);
        return restaurantRepository.save(restaurant);
    }

    public User user(Role role, Restaurant restaurant) {
        return user(role, restaurant, role.name().toLowerCase(Locale.ROOT) + sequence.incrementAndGet() + "@example.test");
    }

    public User user(Role role, Restaurant restaurant, String email) {
        return user(role, restaurant, email, true);
    }

    public User user(Role role, Restaurant restaurant, String email, boolean active) {
        User user = new User(email, passwordHash(), "Usuario " + role.name(), role, restaurant);
        user.setActive(active);
        return userRepository.save(user);
    }

    public Zone zone(Restaurant restaurant) {
        return zone(restaurant, "CENTRAL", 1);
    }

    public Zone zone(Restaurant restaurant, String code, int sortOrder) {
        return zoneRepository.save(new Zone("Zona " + code, code, sortOrder, restaurant));
    }

    public RestaurantTable table(Restaurant restaurant) {
        return table(restaurant, 4, null);
    }

    public RestaurantTable table(Restaurant restaurant, int capacity, Zone zone) {
        return table(restaurant, sequence.incrementAndGet(), capacity, zone, TableStatus.AVAILABLE, true);
    }

    public RestaurantTable table(Restaurant restaurant, TableStatus status) {
        return table(restaurant, sequence.incrementAndGet(), 4, null, status, true);
    }

    public RestaurantTable table(
        Restaurant restaurant,
        int tableNumber,
        int capacity,
        Zone zone,
        TableStatus status,
        boolean active
    ) {
        RestaurantTable table = new RestaurantTable(tableNumber, 1, capacity, 50.0, restaurant);
        table.setZone(zone);
        table.setGridX(1);
        table.setGridY(1);
        table.setStatus(status);
        table.setActive(active);
        return tableRepository.save(table);
    }

    public MenuItem menuItem(Restaurant restaurant) {
        return menuItem(restaurant, "Plato " + sequence.incrementAndGet(), 10.0);
    }

    public MenuItem menuItem(Restaurant restaurant, String name, double price) {
        MenuItem item = new MenuItem(name, MenuCategory.MAIN, price, restaurant);
        item.setProtein("Pollo");
        item.setCondiments("Sal");
        item.setIngredients("Ingrediente de prueba");
        item.setPreparationNotes("Cocinar 10 min");
        return menuItemRepository.save(item);
    }
}
