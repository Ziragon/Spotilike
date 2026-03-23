package com.spotilike.userservice.service;

import com.spotilike.userservice.model.Role;
import com.spotilike.userservice.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        String testSecretKey = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
        jwtService = new JwtService(testSecretKey, 3600000L);
    }

    @Test
    @DisplayName("Должен сгенерировать токен с правильным контрактом для Gateway")
    void shouldGenerateValidToken() {
        // Given
        User user = User.builder()
                .id(1L)
                .username("testUser")
                .email("test@mail.com")
                .roles(Set.of(new Role(1L, "ROLE_USER")))
                .build();

        // When
        String token = jwtService.generateToken(user);

        // Then
        assertThat(jwtService.extractEmail(token)).isEqualTo("test@mail.com");

        Integer userId = jwtService.extractClaim(token, c -> c.get("userId", Integer.class));
        assertThat(userId).isEqualTo(1);

        @SuppressWarnings("unchecked")
        List<String> roles = jwtService.extractClaim(token, c -> c.get("roles", List.class));
        assertThat(roles).containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("Должен вернуть true по корректному токену")
    void isTokenValid_ShouldReturnTrue_WhenTokenCorrect() {
        // Given
        String username = "jane_doe";
        User user = User.builder()
                .id(1L)
                .username(username)
                .email("jane@mail.com")
                .roles(Set.of())
                .build();

        String token = jwtService.generateToken(user);

        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn(username);

        // When
        boolean isValid = jwtService.isTokenValid(token, userDetails);

        // Then
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Должен вернуть false, если email в токене не совпадает с пользователем")
    void isTokenValid_ShouldReturnFalse_WhenEmailDoesNotMatch() {
        // Given
        User user = User.builder()
                .id(1L)
                .email("owner@mail.com")
                .username("owner")
                .roles(Set.of())
                .build();
        String token = jwtService.generateToken(user);

        UserDetails wrongUser = mock(UserDetails.class);
        when(wrongUser.getUsername()).thenReturn("attacker@mail.com");

        // When
        boolean isValid = jwtService.isTokenValid(token, wrongUser);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Должен извлечь userId из claims")
    void shouldExtractUserIdFromClaims() {
        // Given
        User user = User.builder()
                .id(42L).username("user").roles(Set.of()).build();
        String token = jwtService.generateToken(user);

        // When
        Long userId = jwtService.extractClaim(token,
                claims -> claims.get("userId", Long.class));

        // Then
        assertThat(userId).isEqualTo(42L);
    }
}