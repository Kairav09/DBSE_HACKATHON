package com.smartparking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class AuthDtos {

    public record RegisterRequest(
            @NotBlank(message = "fullName is required") String fullName,
            @NotBlank(message = "email is required") @Email(message = "email must be a valid email address") String email,
            @NotBlank(message = "password is required") @Size(min = 6, message = "password must be at least 6 characters") String password,
            String phone
    ) {}

    public record LoginRequest(
            @NotBlank(message = "email is required") @Email(message = "email must be a valid email address") String email,
            @NotBlank(message = "password is required") String password
    ) {}

    public record UserResponse(
            Long id, String fullName, String email, String phone, String role, LocalDateTime createdAt
    ) {}

    /** Slim user object embedded in the login response, per docs/API.md §1 */
    public record UserSummary(Long id, String fullName, String email, String role) {}

    public record LoginResponse(String token, UserSummary user) {}
}
