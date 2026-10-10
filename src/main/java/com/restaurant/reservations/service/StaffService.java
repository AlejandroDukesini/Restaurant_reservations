package com.restaurant.reservations.service;

import com.restaurant.reservations.dto.StaffRequest;
import com.restaurant.reservations.dto.StaffResponse;
import com.restaurant.reservations.model.Restaurant;
import com.restaurant.reservations.model.Role;
import com.restaurant.reservations.model.User;
import com.restaurant.reservations.repository.RestaurantRepository;
import com.restaurant.reservations.repository.UserRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffService {

    private static final List<Role> STAFF_ROLES = List.of(Role.EMPLOYEE, Role.COOK);

    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    public StaffService(
        UserRepository userRepository,
        RestaurantRepository restaurantRepository,
        PasswordEncoder passwordEncoder,
        AuthService authService
    ) {
        this.userRepository = userRepository;
        this.restaurantRepository = restaurantRepository;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
    }

    public List<StaffResponse> getStaff() {
        Long restaurantId = currentRestaurantId();
        return userRepository.findByRestaurantIdAndRoleIn(restaurantId, STAFF_ROLES).stream()
            .sorted(Comparator.comparing(User::getName))
            .map(StaffService::toResponse)
            .toList();
    }

    @Transactional
    public StaffResponse createStaff(StaffRequest request) {
        Long restaurantId = currentRestaurantId();
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow(() -> new IllegalArgumentException("Restaurant not found"));

        String email = normalizeEmail(request.email());
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email already registered");
        }

        String password = nonBlank(request.password());
        if (password == null) {
            throw new IllegalArgumentException("Password is required");
        }

        User user = new User(
            email,
            passwordEncoder.encode(password),
            request.name(),
            parseStaffRole(request.role()),
            restaurant
        );
        user.setActive(request.active());
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public StaffResponse updateStaff(Long id, StaffRequest request) {
        Long restaurantId = currentRestaurantId();
        User user = userRepository.findByIdAndRestaurantId(id, restaurantId)
            .orElseThrow(() -> new IllegalArgumentException("Staff member not found"));

        if (!STAFF_ROLES.contains(user.getRole())) {
            throw new IllegalArgumentException("User is not a staff member");
        }

        String email = normalizeEmail(request.email());
        if (!email.equals(user.getEmail()) && userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email already registered");
        }

        Role role = parseStaffRole(request.role());
        String newPassword = nonBlank(request.password());

        user.setName(request.name());
        user.setEmail(email);
        user.setRole(role);
        user.setActive(request.active());
        if (newPassword != null) {
            user.setPassword(passwordEncoder.encode(newPassword));
        }
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void deleteStaff(Long id) {
        Long restaurantId = currentRestaurantId();
        User user = userRepository.findByIdAndRestaurantId(id, restaurantId)
            .orElseThrow(() -> new IllegalArgumentException("Staff member not found"));
        user.setActive(false);
        userRepository.save(user);
    }

    private Long currentRestaurantId() {
        Long restaurantId = authService.getCurrentUserRestaurantId();
        if (restaurantId == null) {
            throw new IllegalArgumentException("User not associated with a restaurant");
        }
        return restaurantId;
    }

    // El login busca el email en minusculas y sin espacios (AuthService.login): si aqui
    // se guardaba tal cual, una cuenta creada con mayusculas no podia iniciar sesion.
    private static String normalizeEmail(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private static String nonBlank(String value) {
        return value != null && !value.isBlank() ? value : null;
    }

    private static Role parseStaffRole(String value) {
        Role role;
        try {
            role = Role.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid role: " + value);
        }
        if (!STAFF_ROLES.contains(role)) {
            throw new IllegalArgumentException("Role must be EMPLOYEE or COOK");
        }
        return role;
    }

    private static StaffResponse toResponse(User user) {
        return new StaffResponse(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getRole().name(),
            user.isActive(),
            user.getCreatedAt()
        );
    }
}
