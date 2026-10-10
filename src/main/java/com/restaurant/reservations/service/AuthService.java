package com.restaurant.reservations.service;

import com.restaurant.reservations.dto.LoginRequest;
import com.restaurant.reservations.dto.LoginResponse;
import com.restaurant.reservations.dto.RegisterRequest;
import com.restaurant.reservations.dto.RegisterResponse;
import com.restaurant.reservations.model.Role;
import com.restaurant.reservations.model.User;
import com.restaurant.reservations.repository.RestaurantRepository;
import com.restaurant.reservations.repository.UserRepository;
import com.restaurant.reservations.security.JwtTokenProvider;
import com.restaurant.reservations.security.UserPrincipal;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthService(
        AuthenticationManager authenticationManager,
        UserRepository userRepository,
        RestaurantRepository restaurantRepository,
        PasswordEncoder passwordEncoder,
        JwtTokenProvider tokenProvider
    ) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.restaurantRepository = restaurantRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    /**
     * Registro publico de clientes.
     *
     * El rol se fija en el servidor a CUSTOMER: aceptarlo del cuerpo de la peticion
     * permitia a cualquier anonimo crear una cuenta ADMIN (escalada de privilegios).
     * Tampoco se acepta restaurantId: un cliente no queda asociado a la organizacion
     * de ningun restaurante, y esa asociacion era lo que daba alcance de tenant.
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.findByEmail(email).isPresent()) {
            log.info("Registro rechazado: email ya existente");
            throw new IllegalArgumentException("Email already registered");
        }

        User user = new User(
            email,
            passwordEncoder.encode(request.password()),
            request.name().strip(),
            Role.CUSTOMER,
            null
        );

        User saved = userRepository.save(user);
        log.info("Alta de cliente id={} role={}", saved.getId(), saved.getRole());

        // Se devuelve un DTO y no la entidad User: la entidad serializaba el hash
        // BCrypt de la contrasena en la respuesta HTTP.
        return new RegisterResponse(saved.getId(), saved.getEmail(), saved.getName(), saved.getRole().name());
    }

    public LoginResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password())
            );
        } catch (AuthenticationException ex) {
            // Evidencia para deteccion de fuerza bruta / password spraying.
            // No se registra la contrasena ni se distingue "usuario inexistente"
            // de "clave incorrecta" (evita enumeracion de cuentas).
            log.warn("Login fallido para '{}'", email);
            throw ex;
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        String token = tokenProvider.generateToken(authentication);
        log.info("Login correcto userId={} role={}", userPrincipal.getId(), userPrincipal.getRole());

        return new LoginResponse(
            token,
            userPrincipal.getId(),
            userPrincipal.getEmail(),
            userPrincipal.getRole().name(),
            userPrincipal.getRestaurantId()
        );
    }

    public User getCurrentUser() {
        return userRepository.findById(currentPrincipal().getId())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    public Long getCurrentUserId() {
        return currentPrincipal().getId();
    }

    public Role getCurrentUserRole() {
        return currentPrincipal().getRole();
    }

    public Long getCurrentUserRestaurantId() {
        return currentPrincipal().getRestaurantId();
    }

    private static UserPrincipal currentPrincipal() {
        return (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private static String normalizeEmail(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
