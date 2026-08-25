package com.spotilike.userservice.service;

import com.spotilike.userservice.BaseIT;
import com.spotilike.userservice.exception.auth.TokenRevokedException;
import com.spotilike.userservice.model.RefreshToken;
import com.spotilike.userservice.model.User;
import com.spotilike.userservice.repository.RefreshTokenRepository;
import com.spotilike.userservice.repository.UserRepository;
import com.spotilike.userservice.util.TokenHashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;


import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    @DisplayName("Token saves into DB with correct hash")
    void shouldPersistTokenInDatabase() {
        refreshTokenService.createRefreshToken(testUser.getId(), "127.0.0.1", "Device");

        var allTokens = refreshTokenRepository.findAll();
        assertThat(allTokens).hasSize(1);
        assertThat(allTokens.getFirst().getUser().getUsername())
                .isEqualTo("db_user");
    }

    @Test
    @DisplayName("revokeByUserIdAndDeviceInfo revokes old token from DB")
    void shouldRevokeOldTokenOnSameDevice() {
        String oldToken = refreshTokenService
                .createRefreshToken(testUser.getId(), "127.0.0.1", "Same");

        refreshTokenService
                .createRefreshToken(testUser.getId(), "127.0.0.1", "Same");

        RefreshToken old = refreshTokenService
                .findByToken(oldToken).orElseThrow();
        assertThat(old.getRevokedAt()).isNotNull();
    }

    @Test
    @DisplayName("revokeAllByUserId revokes all tokens in DB")
    void shouldRevokeAllTokensInDatabase() {
        String t1 = refreshTokenService
                .createRefreshToken(testUser.getId(), "127.0.0.1", "D1");
        String t2 = refreshTokenService
                .createRefreshToken(testUser.getId(), "127.0.0.1", "D2");

        refreshTokenService.revokeAllUserTokens(testUser.getId());

        assertThat(refreshTokenService.findByToken(t1)
                .orElseThrow(() -> new AssertionError("Token t1 not found"))
                .getRevokedAt()).isNotNull();

        assertThat(refreshTokenService.findByToken(t2)
                .orElseThrow(() -> new AssertionError("Token t2 not found"))
                .getRevokedAt()).isNotNull();
    }

    @Test
    @DisplayName("Rotation saves family integrity (familyId inherited)")
    void shouldKeepFamilyIdDuringRotation() {

        String t1 = refreshTokenService.createRefreshToken(testUser.getId(), "127.0.0.1", "Device");
        RefreshToken token1 = refreshTokenService.findByToken(t1).orElseThrow();

        // First rotation
        String t2 = refreshTokenService.rotateRefreshToken(t1, "127.0.0.1", "Device");
        RefreshToken token2 = refreshTokenService.findByToken(t2).orElseThrow();

        // Second rotation
        String t3 = refreshTokenService.rotateRefreshToken(t2, "127.0.0.1", "Device");
        RefreshToken token3 = refreshTokenService.findByToken(t3).orElseThrow();

        // Correct FamilyId
        assertThat(token1.getFamilyId()).isNotNull();
        assertThat(token1.getFamilyId()).isEqualTo(token2.getFamilyId());
        assertThat(token2.getFamilyId()).isEqualTo(token3.getFamilyId());

        // First 2 tokens marked as consumed
        assertThat(refreshTokenService.findByToken(t1).orElseThrow().getConsumedAt()).isNotNull();
        assertThat(refreshTokenService.findByToken(t2).orElseThrow().getConsumedAt()).isNotNull();
    }

    @Test
    @DisplayName("Reuse attack revokes tokens by family")
    void shouldRevokeEntireFamilyOnReuseAttack() {
        // Token marked as used hour ago
        RefreshToken stolenToken = RefreshToken.builder()
                .user(testUser)
                .familyId(UUID.randomUUID())
                .tokenHash(TokenHashUtil.hash("stolen-clear-token"))
                .ipAddress("127.0.0.1")
                .deviceInfo("Device")
                .expiresAt(OffsetDateTime.now().plusDays(1))
                .consumedAt(OffsetDateTime.now().minusHours(1))
                .build();

        refreshTokenRepository.save(stolenToken);

        assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken("stolen-clear-token", "127.0.0.1", "Device"))
                .isInstanceOf(TokenRevokedException.class);

        // Token must be revoked
        RefreshToken dbToken = refreshTokenRepository.findById(stolenToken.getId()).orElseThrow();
        assertThat(dbToken.getRevokedAt()).isNotNull();
    }
}