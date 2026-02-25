package com.spotilike.userservice.service;

import com.spotilike.userservice.model.Role;
import com.spotilike.userservice.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtServiceTest {

    private JwtService jwtService;

    private final String testSecretKey = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(testSecretKey, 3600000L);
    }

    @Test
    @DisplayName("Должен сгенерировать валидный токен")
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
        assertThat(token).isNotNull();
        assertThat(jwtService.extractUsername(token)).isEqualTo("testUser");
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
    @DisplayName("Должен вернуть false для подделанного username")
    void isTokenValid_ShouldReturnFalse_WhenUsernameDoesNotMatch() {
        // Given
        User user = User.builder()
                .id(1L)
                .username("correct_user")
                .email("email@example.com")
                .roles(Set.of())
                .build();

        String token = jwtService.generateToken(user);

        UserDetails wrongUser = mock(UserDetails.class);
        when(wrongUser.getUsername()).thenReturn("wrong_user");

        // When
        boolean isValid = jwtService.isTokenValid(token, wrongUser);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Должен вернуть false для подделанного токена")
    void shouldReturnFalseForTamperedToken() {
        // Given
        User user = User.builder()
                .id(1L).username("user").roles(Set.of()).build();
        String token = jwtService.generateToken(user);

        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("user");

        // When — подделываем токен
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        // Then
        assertThat(jwtService.isTokenValid(tampered, userDetails)).isFalse();
    }

    @Test
    @DisplayName("Должен вернуть false для пустого токена")
    void shouldReturnFalseForEmptyToken() {
        UserDetails userDetails = mock(UserDetails.class);
        assertThat(jwtService.isTokenValid("", userDetails)).isFalse();
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