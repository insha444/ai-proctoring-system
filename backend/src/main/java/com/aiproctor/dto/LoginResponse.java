package com.aiproctor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String token;

    @Builder.Default
    private String tokenType = "Bearer";

    private Long userId;
    private String name;
    private String email;
    private String role;
    private String message;

    public static LoginResponse success(String token, Long userId,
                                        String name, String email, String role) {
        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(userId)
                .name(name)
                .email(email)
                .role(role)
                .message("Login successful")
                .build();
    }
}
