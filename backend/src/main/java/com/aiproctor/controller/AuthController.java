package com.aiproctor.controller;

import com.aiproctor.dto.LoginRequest;
import com.aiproctor.dto.LoginResponse;
import com.aiproctor.dto.RegisterRequest;
import com.aiproctor.model.User;
import com.aiproctor.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "message", "AI Proctor Backend is running"
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok(Map.of("message", "Account created successfully"));
    }
    @GetMapping("/hashtest")
    public String hashTest() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12).encode("Admin@1234");
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of(
                "id",        user.getId(),
                "name",      user.getName(),
                "email",     user.getEmail(),
                "role",      user.getRole().name(),
                "createdAt", user.getCreatedAt()
        ));
    }
}
