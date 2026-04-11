package com.spotilike.userservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @Schema(example = "user@example.com", description = "Уникальный email пользователя")
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @Schema(example = "Password", minLength = 8)
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 64, message = "Password must be 8-64 characters")
        String password,

        @Schema(example = "Ziragon")
        @NotBlank(message = "Username is required")
        @Size(min = 2, max = 20, message = "Username must be 2-20 characters")
        String username
) {}