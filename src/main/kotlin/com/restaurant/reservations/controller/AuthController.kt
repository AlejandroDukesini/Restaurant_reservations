package com.restaurant.reservations.controller

import com.restaurant.reservations.dto.LoginRequest
import com.restaurant.reservations.dto.LoginResponse
import com.restaurant.reservations.dto.RegisterRequest
import com.restaurant.reservations.dto.RegisterResponse
import com.restaurant.reservations.dto.SessionUserResponse
import com.restaurant.reservations.security.UserPrincipal
import com.restaurant.reservations.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<RegisterResponse> {
        val user = authService.register(request)
        return ResponseEntity.ok(user)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<LoginResponse> {
        val response = authService.login(request)
        return ResponseEntity.ok(response)
    }

    // El filtro JWT ya rechaza tokens caducados y usuarios dados de baja.
    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: UserPrincipal): SessionUserResponse =
        SessionUserResponse(
            userId = principal.id,
            email = principal.email,
            role = principal.role.name,
            restaurantId = principal.restaurantId
        )
}
