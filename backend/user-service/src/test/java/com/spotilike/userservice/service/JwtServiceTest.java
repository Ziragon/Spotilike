package com.spotilike.userservice.service;

import com.spotilike.userservice.model.Role;
import com.spotilike.userservice.model.User;
import org.junit.jupiter.api.BeforeEach;
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
    void shouldGenerateValidToken() {
        // Given
        User user = User.builder()
                .id(1L)
                .username("testUser")
                .roles(Set.of(new Role(1L, "ROLE_USER")))
                .build();

        // When
        String token = jwtService.generateToken(user);

        // Then
        assertThat(token).isNotNull();
        assertThat(jwtService.extractUsername(token)).isEqualTo("testUser");
    }

    @Test
    void isTokenValid_ShouldReturnTrue_WhenTokenCorrect() {
        // Given
        String username = "jane_doe";
        User user = User.builder().username(username).roles(Set.of()).build();
        String token = jwtService.generateToken(user);

        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn(username);

        // When
        boolean isValid = jwtService.isTokenValid(token, userDetails);

        // Then
        assertThat(isValid).isTrue();
    }

    @Test
    void isTokenValid_ShouldReturnFalse_WhenUsernameDoesNotMatch() {
        // Given
        User user = User.builder().username("correct_user").roles(Set.of()).build();
        String token = jwtService.generateToken(user);

        UserDetails wrongUser = mock(UserDetails.class);
        when(wrongUser.getUsername()).thenReturn("wrong_user");

        // When
        boolean isValid = jwtService.isTokenValid(token, wrongUser);

        // Then
        assertThat(isValid).isFalse();
    }
}