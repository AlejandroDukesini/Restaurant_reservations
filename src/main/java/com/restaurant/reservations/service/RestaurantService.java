package com.restaurant.reservations.service;

import com.restaurant.reservations.dto.RestaurantRegistrationRequest;
import com.restaurant.reservations.dto.RestaurantResponse;
import com.restaurant.reservations.exception.ResourceNotFoundException;
import com.restaurant.reservations.exception.ValidationBusinessException;
import com.restaurant.reservations.model.Restaurant;
import com.restaurant.reservations.model.Role;
import com.restaurant.reservations.model.User;
import com.restaurant.reservations.repository.RestaurantRepository;
import com.restaurant.reservations.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RestaurantService {

    private static final Logger log = LoggerFactory.getLogger(RestaurantService.class);

    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final WebsiteGeneratorService websiteGeneratorService;
    private final AuthService authService;

    public RestaurantService(
        RestaurantRepository restaurantRepository,
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        WebsiteGeneratorService websiteGeneratorService,
        AuthService authService
    ) {
        this.restaurantRepository = restaurantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.websiteGeneratorService = websiteGeneratorService;
        this.authService = authService;
    }

    @Transactional
    public RestaurantResponse registerRestaurant(RestaurantRegistrationRequest request) {
        if (restaurantRepository.findBySlug(generateSlug(request.name())).isPresent()) {
            throw new IllegalArgumentException("Restaurant name already exists");
        }

        String slug = generateSlug(request.name());

        // Mismo criterio que el login (minusculas, sin espacios) y unicidad comprobada
        // antes de escribir nada: el duplicado terminaba en un 500 por la restriccion unique.
        String adminEmail = request.adminEmail().strip().toLowerCase(Locale.ROOT);
        if (userRepository.findByEmail(adminEmail).isPresent()) {
            throw new IllegalArgumentException("Email already registered");
        }

        Restaurant restaurant = new Restaurant(
            request.name(),
            slug,
            request.description(),
            request.address(),
            request.phone(),
            request.email(),
            request.numberOfTables(),
            request.numberOfChairs(),
            request.numberOfFloors()
        );

        // websiteUrl es NOT NULL en la tabla: guardar primero con null y actualizar
        // despues hacia fallar todo registro con una violacion de integridad (500).
        // El generador solo necesita slug y datos descriptivos, no el id.
        restaurant.setWebsiteUrl(websiteGeneratorService.generateWebsite(restaurant));
        Restaurant savedRestaurant = restaurantRepository.save(restaurant);

        userRepository.save(new User(
            adminEmail,
            passwordEncoder.encode(request.adminPassword()),
            request.adminName(),
            Role.ADMIN,
            savedRestaurant
        ));

        return toResponse(savedRestaurant);
    }

    public List<RestaurantResponse> getAllRestaurants() {
        return restaurantRepository.findByActiveTrue().stream()
            .map(RestaurantService::toResponse)
            .toList();
    }

    public RestaurantResponse getRestaurantBySlug(String slug) {
        Restaurant restaurant = restaurantRepository.findBySlugAndActiveTrue(slug)
            .orElseThrow(() -> new IllegalArgumentException("Restaurant not found"));
        return toResponse(restaurant);
    }

    public RestaurantResponse getRestaurantById(Long id) {
        Restaurant restaurant = restaurantRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));
        requireOwnRestaurant(id);
        return toResponse(restaurant);
    }

    @Transactional
    public RestaurantResponse updateRestaurant(Long id, RestaurantRegistrationRequest request) {
        Restaurant restaurant = restaurantRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));

        // ADMIN es el administrador de UN restaurante, no un superusuario global:
        // sin este control cualquier admin podia editar o dar de baja el
        // restaurante de otro tenant pasando su id.
        requireOwnRestaurant(id);

        restaurant.setName(request.name());
        restaurant.setDescription(request.description());
        restaurant.setAddress(request.address());
        restaurant.setPhone(request.phone());
        restaurant.setEmail(request.email());
        restaurant.setNumberOfTables(request.numberOfTables());
        restaurant.setNumberOfChairs(request.numberOfChairs());
        restaurant.setNumberOfFloors(request.numberOfFloors());

        return toResponse(restaurantRepository.save(restaurant));
    }

    @Transactional
    public void deleteRestaurant(Long id) {
        Restaurant restaurant = restaurantRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));

        requireOwnRestaurant(id);

        log.warn("Restaurante {} desactivado por userId={}", id, authService.getCurrentUserId());
        restaurant.setActive(false);
        restaurantRepository.save(restaurant);
    }

    private void requireOwnRestaurant(Long id) {
        Long callerRestaurantId = authService.getCurrentUserRestaurantId();
        if (!Objects.equals(callerRestaurantId, id)) {
            log.warn(
                "Acceso cross-tenant a restaurante denegado: userId={} (restaurante {}) pidio restaurante {}",
                authService.getCurrentUserId(), callerRestaurantId, id
            );
            throw new ResourceNotFoundException("Restaurant not found");
        }
    }

    private static String generateSlug(String name) {
        String slug = name.toLowerCase(Locale.ROOT)
            .replace(" ", "-")
            .replaceAll("[^a-z0-9-]", "")
            .replaceAll("^-+|-+$", "");
        // Un slug vacio (nombre solo con simbolos) haria que el generador de sitios
        // escribiera en el directorio raiz de salida en vez de en un subdirectorio.
        if (slug.isEmpty()) {
            throw new ValidationBusinessException("Restaurant name must contain letters or numbers");
        }
        return slug;
    }

    private static RestaurantResponse toResponse(Restaurant restaurant) {
        return new RestaurantResponse(
            restaurant.getId(),
            restaurant.getName(),
            restaurant.getSlug(),
            restaurant.getDescription(),
            restaurant.getAddress(),
            restaurant.getPhone(),
            restaurant.getEmail(),
            restaurant.getNumberOfTables(),
            restaurant.getNumberOfChairs(),
            restaurant.getNumberOfFloors(),
            restaurant.getWebsiteUrl(),
            restaurant.getCreatedAt().toString(),
            restaurant.isActive()
        );
    }
}
