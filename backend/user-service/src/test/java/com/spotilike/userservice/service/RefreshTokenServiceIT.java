package com.spotilike.userservice.service;

import com.spotilike.userservice.BaseIT;
import com.spotilike.userservice.model.RefreshToken;
import com.spotilike.userservice.model.User;
import com.spotilike.userservice.repository.RefreshTokenRepository;
import com.spotilike.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;


import static org.assertj.core.api.Assertions.assertThat;

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
    @DisplayName("Токен сохраняется в БД с правильным хешем")
    void shouldPersistTokenInDatabase() {
        refreshTokenService.createRefreshToken(testUser.getId(), "127.0.0.1", "Device");

        var allTokens = refreshTokenRepository.findAll();
        assertThat(allTokens).hasSize(1);
        assertThat(allTokens.getFirst().getUser().getUsername())
                .isEqualTo("db_user");
    }

    @Test
    @DisplayName("revokeByUserIdAndDeviceInfo отзывает старый токен в БД")
    void shouldRevokeOldTokenOnSameDevice() {
        String oldToken = refreshTokenService
                .createRefreshToken(testUser.getId(), "127.0.0.1", "Same");

        refreshTokenService
                .createRefreshToken(testUser.getId(), "127.0.0.1", "Same");

        RefreshToken old = refreshTokenService
                .findByToken(oldToken).orElseThrow();
        assertThat(old.isRevoked()).isTrue();
    }

    @Test
    @DisplayName("revokeAllByUserId отзывает все токены в БД")
    void shouldRevokeAllTokensInDatabase() {
        String t1 = refreshTokenService
                .createRefreshToken(testUser.getId(), "127.0.0.1", "D1");
        String t2 = refreshTokenService
                .createRefreshToken(testUser.getId(), "127.0.0.1", "D2");

        refreshTokenService.revokeAllUserTokens(testUser.getId());

        assertThat(refreshTokenService.findByToken(t1).get().isRevoked())
                .isTrue();
        assertThat(refreshTokenService.findByToken(t2).get().isRevoked())
                .isTrue();
    }
}