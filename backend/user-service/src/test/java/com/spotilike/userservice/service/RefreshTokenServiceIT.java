package com.spotilike.userservice.service;

import com.spotilike.userservice.BaseIT;
import com.spotilike.userservice.model.RefreshToken;
import com.spotilike.userservice.model.User;
import com.spotilike.userservice.exception.auth.TokenExpiredException;
import com.spotilike.userservice.repository.RefreshTokenRepository;
import com.spotilike.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RefreshTokenServiceIT extends BaseIT {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .username("db_user")
                .email("test@mail.com")
                .passwordHash("123123123")
                .build();
        userRepository.save(testUser);
    }

    @Test
    @DisplayName("Должен успешно создать токен и сохранить его хеш в БД")
    void shouldCreateAndSaveRefreshToken() {
        // When
        String clearToken = refreshTokenService.createRefreshToken(testUser.getId(), "127.0.0.1", "Test-Device");

        // Then
        assertThat(clearToken).isNotNull();

        // проверка на хеш токена в бд
        Optional<RefreshToken> storedToken = refreshTokenService.findByToken(clearToken);
        assertThat(storedToken).isPresent();
        assertThat(storedToken.get().getUser().getUsername()).isEqualTo("db_user");
        assertThat(storedToken.get().getTokenHash()).isNotEqualTo(clearToken);
    }

    @Test
    @DisplayName("Должен выбросить исключение и удалить токен, если срок истек")
    void verifyExpiration_ShouldThrowExceptionWhenExpired() {
        // Given
        RefreshToken expiredToken = RefreshToken.builder()
                .tokenHash("some-hash")
                .user(testUser)
                .expiresAt(LocalDateTime.now().minusMinutes(1)) // Просрочен
                .ipAddress("127.0.0.1")
                .deviceInfo("Device")
                .build();
        refreshTokenRepository.save(expiredToken);

        // When & Then
        assertThatThrownBy(() -> refreshTokenService.validateRefreshToken(expiredToken.getTokenHash()))
                .isInstanceOf(TokenExpiredException.class);

        // проверка на удаление из бд
        assertThat(refreshTokenRepository.findById(expiredToken.getId())).isEmpty();
    }

    @Test
    @DisplayName("Должен успешно отозвать токен")
    void shouldRevokeToken() {
        // Given
        String clearToken = refreshTokenService.createRefreshToken(testUser.getId(), "127.0.0.1", "Device");

        // When
        refreshTokenService.revokeToken(clearToken);

        // Then
        RefreshToken revokedToken = refreshTokenService.findByToken(clearToken).get();
        assertThat(revokedToken.isRevoked()).isTrue();
    }
}