package com.scalablerest.auth.controller;

import com.scalablerest.auth.dto.AuthResponse;
import com.scalablerest.auth.dto.LoginRequest;
import com.scalablerest.auth.dto.RegisterRequest;
import com.scalablerest.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public String registerUser(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return "USER REGISTERED!";
    }

    @PostMapping("/login")
    public AuthResponse loginUser(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
