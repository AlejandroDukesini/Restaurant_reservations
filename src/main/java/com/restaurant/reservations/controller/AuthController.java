package com.restaurant.reservations.controller;

import com.restaurant.reservations.dto.LoginRequest;
import com.restaurant.reservations.dto.LoginResponse;
import com.restaurant.reservations.dto.RegisterRequest;
import com.restaurant.reservations.dto.RegisterResponse;
import com.restaurant.reservations.dto.SessionUserResponse;
import com.restaurant.reservations.security.UserPrincipal;
import com.restaurant.reservations.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // El filtro JWT ya rechaza tokens caducados y usuarios dados de baja.
    @GetMapping("/me")
    public SessionUserResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return new SessionUserResponse(
            principal.getId(),
            principal.getEmail(),
            principal.getRole().name(),
            principal.getRestaurantId()
        );
    }
}
