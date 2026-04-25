package com.aiproctor.service;

import com.aiproctor.dto.LoginRequest;
import com.aiproctor.dto.LoginResponse;
import com.aiproctor.dto.RegisterRequest;
import com.aiproctor.model.User;
import com.aiproctor.repository.UserRepository;
import com.aiproctor.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Register a new user
     */
    @Transactional
    public void register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        User.Role role;
        try {
            role = User.Role.valueOf(
                    request.getRole() != null
                            ? request.getRole().toUpperCase()
                            : "STUDENT"
            );
        } catch (IllegalArgumentException e) {
            role = User.Role.STUDENT;
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .build();

        userRepository.save(user);

        log.info("New user registered: {} ({})", request.getEmail(), role);
    }

    /**
     * Login and generate JWT token
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {

        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = (User) auth.getPrincipal();

        String token = jwtTokenProvider.generateToken(
                user,
                Map.of(
                        "role", user.getRole().name(),
                        "userId", user.getId(),
                        "name", user.getName()
                )
        );

        log.info("User logged in: {} ({})", user.getEmail(), user.getRole());

        return LoginResponse.success(
                token,
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name()
        );
    }

    /**
     * Get current user profile
     */
    @Transactional(readOnly = true)
    public User getProfile(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
    }
}
