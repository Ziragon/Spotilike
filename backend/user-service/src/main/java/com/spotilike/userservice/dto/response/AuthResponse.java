package com.spotilike.userservice.dto.response;

public record AuthResponse(
        String accessToken,

        String refreshToken,

        long expiresIn,

        UserDto userDto
) {}
