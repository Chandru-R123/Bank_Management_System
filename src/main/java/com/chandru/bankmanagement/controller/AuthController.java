package com.chandru.bankmanagement.controller;

import com.chandru.bankmanagement.dto.LoginRequest;
import com.chandru.bankmanagement.dto.LoginResponse;
import com.chandru.bankmanagement.service.AuthService;
import org.springframework.web.bind.annotation.*;
import com.chandru.bankmanagement.dto.RegisterRequest;
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }
    @PostMapping("/register")
    public String register(@RequestBody RegisterRequest request) {
        return authService.register(request);
    }
}