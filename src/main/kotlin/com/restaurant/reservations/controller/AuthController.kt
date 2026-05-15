package com.restaurant.reservations.controller

import com.restaurant.reservations.dto.LoginRequest
import com.restaurant.reservations.dto.LoginResponse
import com.restaurant.reservations.dto.RegisterRequest
import com.restaurant.reservations.model.User
import com.restaurant.reservations.service.AuthService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {
    
    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest): ResponseEntity<User> {
        val user = authService.register(request)
        return ResponseEntity.ok(user)
    }
    
    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<LoginResponse> {
        val response = authService.login(request)
        return ResponseEntity.ok(response)
    }
}
