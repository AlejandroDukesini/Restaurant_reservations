package com.restaurant.reservations.service;

import com.restaurant.reservations.dto.StaffRequest;
import com.restaurant.reservations.dto.StaffResponse;
import com.restaurant.reservations.exception.ResourceNotFoundException;
import com.restaurant.reservations.model.Permission;
import com.restaurant.reservations.model.Restaurant;
import com.restaurant.reservations.model.Role;
import com.restaurant.reservations.model.StaffGroup;
import com.restaurant.reservations.model.User;
import com.restaurant.reservations.repository.DeliveryDriverRepository;
import com.restaurant.reservations.repository.RestaurantRepository;
import com.restaurant.reservations.repository.StaffGroupRepository;
import com.restaurant.reservations.repository.UserRepository;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Personal del restaurante. Solo el gerente lo administra.
 *
 * Contra la escalada de privilegios:
 *  - Solo se crean/editan cuentas EMPLOYEE, COOK o DELIVERY: nunca ADMIN (ni CUSTOMER).
 *  - Las cuentas de gerente no son "personal": no se pueden editar ni desactivar desde aqui,
 *    asi que nadie (tampoco el propio gerente) cambia su rol o permisos por esta via.
 *  - Los permisos adicionales salen de una lista blanca sin capacidades de gerente.
 */
@Service
public class StaffService {

    private static final Logger log = LoggerFactory.getLogger(StaffService.class);
    private static final List<Role> STAFF_ROLES = List.of(Role.EMPLOYEE, Role.COOK, Role.DELIVERY);

    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final StaffGroupRepository groupRepository;
    private final DeliveryDriverRepository driverRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessService access;

    public StaffService(
        UserRepository userRepository,
        RestaurantRepository restaurantRepository,
        StaffGroupRepository groupRepository,
        DeliveryDriverRepository driverRepository,
        PasswordEncoder passwordEncoder,
        AccessService access
    ) {
        this.userRepository = userRepository;
        this.restaurantRepository = restaurantRepository;
        this.groupRepository = groupRepository;
        this.driverRepository = driverRepository;
        this.passwordEncoder = passwordEncoder;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<StaffResponse> getStaff() {
        access.requireManager();
        return userRepository.findByRestaurantIdAndRoleIn(access.restaurantId(), STAFF_ROLES).stream()
            .sorted(Comparator.comparing(User::getName))
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public StaffResponse createStaff(StaffRequest request) {
        access.requireManager();
        Long restaurantId = access.restaurantId();
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

        User user = new User(email, passwordEncoder.encode(password), request.name().strip(),
            parseStaffRole(request.role()), restaurant);
        user.setActive(request.active());
        user.setPhone(nonBlank(request.phone()));
        user.setPermissions(Permission.format(parsePermissions(request.permissions())));
        User saved = userRepository.save(user);
        log.info("Alta de personal id={} role={} por userId={}", saved.getId(), saved.getRole(), access.userId());
        return toResponse(saved);
    }

    @Transactional
    public StaffResponse updateStaff(Long id, StaffRequest request) {
        access.requireManager();
        User user = staffMember(id);

        String email = normalizeEmail(request.email());
        if (!email.equals(user.getEmail()) && userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email already registered");
        }

        Role role = parseStaffRole(request.role());
        String newPassword = nonBlank(request.password());

        user.setName(request.name().strip());
        user.setEmail(email);
        user.setRole(role);
        user.setActive(request.active());
        user.setPhone(nonBlank(request.phone()));
        if (request.permissions() != null) {
            Set<Permission> permissions = parsePermissions(request.permissions());
            log.info("Permisos de userId={} -> {} (por userId={})", id, permissions, access.userId());
            user.setPermissions(Permission.format(permissions));
        }
        if (newPassword != null) {
            user.setPassword(passwordEncoder.encode(newPassword));
        }
        unlinkDriverIfNeeded(user);
        return toResponse(userRepository.save(user));
    }

    /** Baja logica: la cuenta deja de poder iniciar sesion (el filtro JWT rechaza inactivos). */
    @Transactional
    public void deleteStaff(Long id) {
        access.requireManager();
        User user = staffMember(id);
        user.setActive(false);
        userRepository.save(user);
        log.info("Baja de personal id={} por userId={}", id, access.userId());
    }

    private User staffMember(Long id) {
        User user = userRepository.findByIdAndRestaurantId(id, access.restaurantId())
            .orElseThrow(() -> new ResourceNotFoundException("Staff member not found"));
        if (!STAFF_ROLES.contains(user.getRole())) {
            // Gerentes y clientes no se gestionan aqui (evita degradar o modificar al gerente).
            throw new ResourceNotFoundException("Staff member not found");
        }
        return user;
    }

    /** Si deja de ser domiciliario (o se desactiva), se desvincula de su ficha de reparto. */
    private void unlinkDriverIfNeeded(User user) {
        if (user.getRole() != Role.DELIVERY) {
            driverRepository.findByUserId(user.getId()).ifPresent(driver -> {
                driver.setUser(null);
                driverRepository.save(driver);
            });
        }
    }

    private StaffResponse toResponse(User user) {
        List<String> groups = groupRepository.findActiveByMember(user.getId()).stream()
            .map(StaffGroup::getName).toList();
        return new StaffResponse(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getRole().name(),
            user.isActive(),
            user.getCreatedAt(),
            user.getPhone(),
            Permission.parse(user.getPermissions()).stream().map(Enum::name).sorted().toList(),
            groups
        );
    }

    // El login busca el email en minusculas y sin espacios (AuthService.login): si aqui
    // se guardaba tal cual, una cuenta creada con mayusculas no podia iniciar sesion.
    private static String normalizeEmail(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private static String nonBlank(String value) {
        return value != null && !value.isBlank() ? value.strip() : null;
    }

    private static Set<Permission> parsePermissions(List<String> values) {
        Set<Permission> result = EnumSet.noneOf(Permission.class);
        if (values == null) {
            return result;
        }
        for (String value : values) {
            try {
                result.add(Permission.valueOf(value.strip().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException | NullPointerException ex) {
                // Un permiso desconocido es un intento de conceder algo que no existe: se rechaza.
                throw new IllegalArgumentException("Invalid permission: " + value);
            }
        }
        return result;
    }

    private static Role parseStaffRole(String value) {
        Role role;
        try {
            role = Role.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid role: " + value);
        }
        if (!STAFF_ROLES.contains(role)) {
            throw new IllegalArgumentException("Role must be EMPLOYEE, COOK or DELIVERY");
        }
        return role;
    }
}
