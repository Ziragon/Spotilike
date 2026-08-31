package com.spotilike.userservice.service;

import com.spotilike.userservice.model.Role;
import com.spotilike.userservice.model.User;
import com.spotilike.userservice.model.enums.RoleName;
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
    @DisplayName("Should generate valid token for Gateway")
    void shouldGenerateValidToken() {
        User user = User.builder()
                .id(1L)
                .username("testUser")
                .email("test@mail.com")
                .roles(Set.of(new Role(1L, RoleName.ROLE_USER)))
                .build();

        String token = jwtService.generateToken(user);

        assertThat(jwtService.extractEmail(token)).isEqualTo("test@mail.com");

        Integer userId = jwtService.extractClaim(token, c -> c.get("userId", Integer.class));
        assertThat(userId).isEqualTo(1);

        @SuppressWarnings("unchecked")
        List<String> roles = jwtService.extractClaim(token, c -> c.get("roles", List.class));
        assertThat(roles).containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("Should return true with correct token")
    void isTokenValid_ShouldReturnTrue_WhenTokenCorrect() {
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

        boolean isValid = jwtService.isTokenValid(token, userDetails);

        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Should return false, if email in token does not match to user")
    void isTokenValid_ShouldReturnFalse_WhenEmailDoesNotMatch() {
        User user = User.builder()
                .id(1L)
                .email("owner@mail.com")
                .username("owner")
                .roles(Set.of())
                .build();
        String token = jwtService.generateToken(user);

        UserDetails wrongUser = mock(UserDetails.class);
        when(wrongUser.getUsername()).thenReturn("attacker@mail.com");

        boolean isValid = jwtService.isTokenValid(token, wrongUser);

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should extract userId from claims")
    void shouldExtractUserIdFromClaims() {
        User user = User.builder()
                .id(42L).username("user").roles(Set.of()).build();
        String token = jwtService.generateToken(user);

        Long userId = jwtService.extractClaim(token,
                claims -> claims.get("userId", Long.class));

        assertThat(userId).isEqualTo(42L);
    }
}
