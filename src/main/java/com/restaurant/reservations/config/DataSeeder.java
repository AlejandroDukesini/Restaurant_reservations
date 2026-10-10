package com.restaurant.reservations.config;

import com.restaurant.reservations.model.MenuCategory;
import com.restaurant.reservations.model.MenuItem;
import com.restaurant.reservations.model.Restaurant;
import com.restaurant.reservations.model.RestaurantTable;
import com.restaurant.reservations.model.Role;
import com.restaurant.reservations.model.User;
import com.restaurant.reservations.model.Zone;
import com.restaurant.reservations.repository.MenuItemRepository;
import com.restaurant.reservations.repository.RestaurantRepository;
import com.restaurant.reservations.repository.TableRepository;
import com.restaurant.reservations.repository.UserRepository;
import com.restaurant.reservations.repository.ZoneRepository;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String DEFAULT_SEED_PASSWORD = "password123";

    private final RestaurantRepository restaurantRepository;
    private final ZoneRepository zoneRepository;
    private final TableRepository tableRepository;
    private final UserRepository userRepository;
    private final MenuItemRepository menuItemRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean seedEnabled;
    private final String seedPassword;

    public DataSeeder(
        RestaurantRepository restaurantRepository,
        ZoneRepository zoneRepository,
        TableRepository tableRepository,
        UserRepository userRepository,
        MenuItemRepository menuItemRepository,
        PasswordEncoder passwordEncoder,
        // La siembra de demostracion crea cuentas con una contrasena conocida y
        // publicada en la pantalla de login. Se puede desactivar (SEED_ENABLED=false)
        // o cambiar la contrasena (SEED_PASSWORD) sin tocar el codigo.
        @Value("${app.seed.enabled:true}") boolean seedEnabled,
        @Value("${app.seed.password:password123}") String seedPassword
    ) {
        this.restaurantRepository = restaurantRepository;
        this.zoneRepository = zoneRepository;
        this.tableRepository = tableRepository;
        this.userRepository = userRepository;
        this.menuItemRepository = menuItemRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedEnabled = seedEnabled;
        this.seedPassword = seedPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            log.info("Siembra de datos de demostracion desactivada (SEED_ENABLED=false)");
            return;
        }
        if (restaurantRepository.count() > 0) {
            return;
        }

        if (DEFAULT_SEED_PASSWORD.equals(seedPassword)) {
            log.warn(
                "Sembrando cuentas de demostracion con la contrasena por defecto. "
                    + "En un despliegue real define SEED_PASSWORD o SEED_ENABLED=false."
            );
        }

        Restaurant restaurant = new Restaurant(
            "Maison Noir",
            "maison-noir",
            "Experiencia gastronómica premium multi-tenant",
            "Av. Premium 1200, Ciudad",
            "+57 300 000 0000",
            "reservas@maisonnoir.com",
            16,
            56,
            1
        );
        restaurant.setWebsiteUrl("https://maisonnoir.com");
        restaurant = restaurantRepository.save(restaurant);

        seedUsers(restaurant);
        seedMenu(restaurant);

        List<ZoneSeed> zoneSpecs = List.of(
            new ZoneSeed("Vistas a la Ventana", "WINDOW", 1),
            new ZoneSeed("Cerca de la Cocina", "KITCHEN", 2),
            new ZoneSeed("Zona Central", "CENTRAL", 3),
            new ZoneSeed("Barra de Cócteles", "BAR", 4),
            new ZoneSeed("Terraza Exterior", "TERRACE", 5)
        );

        List<Zone> zones = new ArrayList<>();
        for (ZoneSeed spec : zoneSpecs) {
            zones.add(zoneRepository.save(new Zone(spec.name(), spec.code(), spec.sortOrder(), restaurant)));
        }

        List<TableSeed> tableSpecs = List.of(
            new TableSeed(1, 2, 1, 1, 0, "Ventana 1"),
            new TableSeed(2, 2, 2, 1, 0, "Ventana 2"),
            new TableSeed(3, 4, 3, 1, 0, "Ventana 3"),
            new TableSeed(4, 4, 1, 1, 1, null),
            new TableSeed(5, 6, 2, 1, 1, null),
            new TableSeed(6, 2, 3, 1, 1, null),
            new TableSeed(7, 4, 1, 1, 2, null),
            new TableSeed(8, 4, 2, 1, 2, null),
            new TableSeed(9, 8, 3, 1, 2, "Mesa Familiar"),
            new TableSeed(10, 6, 2, 2, 2, "Mesa VIP"),
            new TableSeed(11, 2, 1, 1, 3, "Barra 1"),
            new TableSeed(12, 2, 2, 1, 3, "Barra 2"),
            new TableSeed(13, 2, 3, 1, 3, "Barra 3"),
            new TableSeed(14, 4, 1, 1, 4, null),
            new TableSeed(15, 6, 2, 1, 4, null),
            new TableSeed(16, 2, 3, 1, 4, null)
        );

        for (TableSeed spec : tableSpecs) {
            RestaurantTable table = new RestaurantTable(
                spec.tableNumber(), 1, spec.capacity(), priceFor(spec.capacity()), restaurant
            );
            table.setName(spec.name());
            table.setZone(zones.get(spec.zoneIndex()));
            table.setGridX(spec.gridX());
            table.setGridY(spec.gridY());
            tableRepository.save(table);
        }
    }

    private static double priceFor(int capacity) {
        if (capacity <= 2) {
            return 45.0;
        }
        if (capacity <= 4) {
            return 65.0;
        }
        return 95.0;
    }

    private void seedUsers(Restaurant restaurant) {
        List<UserSeed> users = List.of(
            new UserSeed("admin@maisonnoir.com", "Admin Maison", Role.ADMIN),
            new UserSeed("mesero1@maisonnoir.com", "Carlos Mesero", Role.EMPLOYEE),
            new UserSeed("mesero2@maisonnoir.com", "Lucía Mesera", Role.EMPLOYEE),
            new UserSeed("cocina1@maisonnoir.com", "Chef Alejo", Role.COOK),
            new UserSeed("cocina2@maisonnoir.com", "Chef Marta", Role.COOK)
        );
        for (UserSeed seed : users) {
            // Contraseña de demo para todos (configurable con SEED_PASSWORD).
            userRepository.save(
                new User(seed.email(), passwordEncoder.encode(seedPassword), seed.name(), seed.role(), restaurant)
            );
        }
    }

    private void seedMenu(Restaurant restaurant) {
        List<MenuSeed> items = List.of(
            new MenuSeed(
                "Bruschetta Trufada", MenuCategory.STARTER, 18.0,
                "Ninguna",
                "Aceite de oliva, sal Maldon, pimienta",
                "Pan de masa madre, tomate cherry, aceite de trufa, albahaca",
                "Tostar el pan, montar en frío, servir de inmediato"
            ),
            new MenuSeed(
                "Carpaccio de Res", MenuCategory.STARTER, 24.0,
                "Res (lomo fino)",
                "Limón, alcaparras, parmesano, aceite de oliva",
                "Lomo de res laminado, rúcula, láminas de parmesano",
                "Laminar en frío, aliñar al momento"
            ),
            new MenuSeed(
                "Risotto de Hongos", MenuCategory.MAIN, 32.0,
                "Ninguna",
                "Mantequilla, parmesano, tomillo, sal, pimienta",
                "Arroz arborio, hongos portobello, caldo de verduras, vino blanco",
                "Cocción lenta 18 min removiendo, mantecar al final"
            ),
            new MenuSeed(
                "Salmón a la Parrilla", MenuCategory.MAIN, 38.0,
                "Salmón",
                "Eneldo, limón, sal, pimienta, mantequilla",
                "Filete de salmón, espárragos, puré de papa",
                "Parrilla 4 min por lado, piel crujiente"
            ),
            new MenuSeed(
                "Filete Mignon", MenuCategory.MAIN, 46.0,
                "Res (filete mignon)",
                "Sal, pimienta, romero, mantequilla, salsa de vino tinto",
                "Medallón de res, papas confitadas, reducción de vino",
                "Sellar y terminar al horno; preguntar término de cocción"
            ),
            new MenuSeed(
                "Pollo al Curry", MenuCategory.MAIN, 30.0,
                "Pollo",
                "Curry, comino, cúrcuma, jengibre, leche de coco",
                "Pechuga de pollo, arroz basmati, cilantro",
                "Guisar 20 min, servir con arroz aparte"
            ),
            new MenuSeed(
                "Tiramisú", MenuCategory.DESSERT, 14.0,
                "Ninguna",
                "Cacao, café espresso",
                "Mascarpone, bizcochos savoiardi, café, huevo",
                "Montar en capas, reposar en frío mínimo 4h"
            ),
            new MenuSeed(
                "Coulant de Chocolate", MenuCategory.DESSERT, 16.0,
                "Ninguna",
                "Azúcar glas",
                "Chocolate 70%, mantequilla, huevo, harina, helado de vainilla",
                "Horno 220°C por 9 min, centro líquido; servir caliente"
            ),
            new MenuSeed(
                "Copa de Vino Tinto", MenuCategory.DRINK, 12.0,
                null,
                null,
                "Reserva Malbec",
                "Servir a 16-18°C"
            ),
            new MenuSeed(
                "Cóctel de la Casa", MenuCategory.DRINK, 15.0,
                null,
                "Angostura, twist de naranja",
                "Gin premium, vermut, licor de naranja",
                "Mezclar con hielo, colar, decorar con twist"
            )
        );
        for (MenuSeed seed : items) {
            MenuItem item = new MenuItem(seed.name(), seed.category(), seed.price(), restaurant);
            item.setProtein(seed.protein());
            item.setCondiments(seed.condiments());
            item.setIngredients(seed.ingredients());
            item.setPreparationNotes(seed.prep());
            menuItemRepository.save(item);
        }
    }

    private record ZoneSeed(String name, String code, int sortOrder) {
    }

    private record TableSeed(int tableNumber, int capacity, int gridX, int gridY, int zoneIndex, String name) {
    }

    private record UserSeed(String email, String name, Role role) {
    }

    private record MenuSeed(
        String name,
        MenuCategory category,
        double price,
        String protein,
        String condiments,
        String ingredients,
        String prep
    ) {
    }
}
