package com.spotilike.userservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @Schema(example = "uuid-token", description = "Строка refresh-токена")
        @NotBlank(message = "Token is required")
        String refreshToken
) {}
