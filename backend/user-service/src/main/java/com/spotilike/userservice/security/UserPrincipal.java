package com.spotilike.userservice.security;

import java.util.List;

public record UserPrincipal(
        Integer userId,
        String email,
        List<String> roles,
        boolean anonymous
) {
    public boolean isAuthenticated() {
        return !anonymous;
    }
}