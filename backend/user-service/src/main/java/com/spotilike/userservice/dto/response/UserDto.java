package com.spotilike.userservice.dto.response;

import com.spotilike.userservice.model.User;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.stream.Collectors;

public record UserDto (
        Long userId,

        String email,

        String username,

        String avatarUrl,

        boolean isVerified,

        Set<String> roles,

        OffsetDateTime createdAt
) {
    public static UserDto from(User user) {
        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getAvatarUrl(),
                user.isVerified(),
                user.getRoles().stream()
                        .map(role -> role.getName().name())
                        .collect(Collectors.toSet()),
                user.getCreatedAt()
        );
    }
}
