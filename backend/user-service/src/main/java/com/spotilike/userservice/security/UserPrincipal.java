package com.spotilike.userservice.security;

import org.jspecify.annotations.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public record UserPrincipal(
        Integer userId,
        String email,
        List<String> roles,
        boolean anonymous
) implements UserDetails {

    @Override
    @NonNull
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

    @Override
    public String getPassword() {
        // В вашей архитектуре пароль проверяется на шлюзе
        return "";
    }

    @Override
    @NonNull
    public String getUsername() {
        return email;
    }
}