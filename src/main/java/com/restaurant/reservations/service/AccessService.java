package com.restaurant.reservations.service;

import com.restaurant.reservations.exception.ResourceNotFoundException;
import com.restaurant.reservations.exception.ValidationBusinessException;
import com.restaurant.reservations.model.Permission;
import com.restaurant.reservations.model.Role;
import com.restaurant.reservations.model.User;
import com.restaurant.reservations.repository.UserRepository;
import com.restaurant.reservations.security.UserPrincipal;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Punto unico de autorizacion de negocio (ademas de las reglas por ruta de SecurityConfig).
 *
 * Todo se decide en el servidor con datos de la base: el rol y el restaurante vienen del
 * usuario cargado en cada peticion por el filtro JWT, y los permisos adicionales se leen de
 * users.permissions (no del token ni del almacenamiento del navegador).
 */
@Service
public class AccessService {

    private static final Logger log = LoggerFactory.getLogger(AccessService.class);

    private final UserRepository userRepository;

    public AccessService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new AccessDeniedException("Not authenticated");
        }
        return principal;
    }

    public Long userId() {
        return principal().getId();
    }

    public Role role() {
        return principal().getRole();
    }

    public Long restaurantId() {
        Long restaurantId = principal().getRestaurantId();
        if (restaurantId == null) {
            throw new ValidationBusinessException("User not associated with a restaurant");
        }
        return restaurantId;
    }

    public User user() {
        return userRepository.findById(userId())
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    public boolean isManager() {
        return role() == Role.ADMIN;
    }

    /** El gerente tiene todos los permisos; el resto solo los concedidos explicitamente. */
    public boolean can(Permission permission) {
        if (isManager()) {
            return true;
        }
        return permissionsOf(user()).contains(permission);
    }

    public void require(Permission permission) {
        if (!can(permission)) {
            log.warn("Permiso {} denegado a userId={} role={}", permission, userId(), role());
            throw new AccessDeniedException("Access denied");
        }
    }

    public void requireManager() {
        if (!isManager()) {
            log.warn("Operacion de gerente denegada a userId={} role={}", userId(), role());
            throw new AccessDeniedException("Access denied");
        }
    }

    public boolean hasRole(Role... roles) {
        Role current = role();
        for (Role r : roles) {
            if (r == current) {
                return true;
            }
        }
        return false;
    }

    /**
     * Recurso de otro restaurante: 404 (no 403) para no confirmar que existe.
     */
    public void requireSameRestaurant(Long ownerRestaurantId, String message) {
        if (!Objects.equals(ownerRestaurantId, restaurantId())) {
            log.warn("Acceso cross-tenant denegado: userId={} pidio recurso del restaurante {}", userId(), ownerRestaurantId);
            throw new ResourceNotFoundException(message);
        }
    }

    public static Set<Permission> permissionsOf(User user) {
        if (user.getRole() == Role.ADMIN) {
            return java.util.EnumSet.allOf(Permission.class);
        }
        return Permission.parse(user.getPermissions());
    }
}
