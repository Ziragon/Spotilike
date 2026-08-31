package com.spotilike.userservice.service;

import com.spotilike.userservice.exception.auth.TokenExpiredException;
import com.spotilike.userservice.exception.auth.TokenRevokedException;
import com.spotilike.userservice.exception.auth.TokenNotFoundException;
import com.spotilike.userservice.exception.resource.UserNotFoundException;
import com.spotilike.userservice.model.RefreshToken;
import com.spotilike.userservice.model.User;
import com.spotilike.userservice.repository.RefreshTokenRepository;
import com.spotilike.userservice.repository.UserRepository;
import com.spotilike.userservice.util.TokenHashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.*;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant FIXED_INSTANT =
            Instant.parse("2025-01-15T12:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final long REFRESH_EXPIRATION_MS = 604_800_000L; // 7 days

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User testUser;

    @BeforeEach
    void setUp() {
        // Fixed clocks
        Clock fixedClock = Clock.fixed(FIXED_INSTANT, ZONE);
        ReflectionTestUtils.setField(refreshTokenService, "clock", fixedClock);
        ReflectionTestUtils.setField(refreshTokenService, "refreshExpiration", REFRESH_EXPIRATION_MS);
        ReflectionTestUtils.setField(refreshTokenService, "gracePeriodSeconds", 30L);

        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@mail.com")
                .build();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(FIXED_INSTANT, ZONE);
    }

    private RefreshToken buildToken(String clearToken, OffsetDateTime revokedAt, OffsetDateTime expiresAt) {
        return RefreshToken.builder()
                .id(1L)
                .user(testUser)
                .familyId(java.util.UUID.randomUUID())
                .tokenHash(TokenHashUtil.hash(clearToken))
                .ipAddress("127.0.0.1")
                .deviceInfo("Test-Device")
                .revokedAt(revokedAt)
                .expiresAt(expiresAt)
                .build();
    }

    @Nested
    @DisplayName("createRefreshToken")
    class CreateRefreshToken {

        @Test
        @DisplayName("Creates token and save to repository")
        void shouldCreateAndSave() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

            refreshTokenService.createRefreshToken(1L, "127.0.0.1", "Device");

            ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository).save(captor.capture());

            RefreshToken saved = captor.getValue();

            assertThat(saved.getRevokedAt()).isNull();
            assertThat(saved.getExpiresAt()).isEqualTo(now().plusNanos(REFRESH_EXPIRATION_MS * 1_000_000));
        }

        @Test
        @DisplayName("Revokes old tokens on same device")
        void shouldRevokeOldTokensOnSameDevice() {
            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(testUser));
            when(refreshTokenRepository
                    .revokeByUserIdAndDeviceInfo(1L, "Same-Device"))
                    .thenReturn(1);

            refreshTokenService
                    .createRefreshToken(1L, "127.0.0.1", "Same-Device");

            verify(refreshTokenRepository)
                    .revokeByUserIdAndDeviceInfo(1L, "Same-Device");
        }

        @Test
        @DisplayName("Throws UserNotFoundException for user who does not exist")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findById(999L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> refreshTokenService
                    .createRefreshToken(999L, "127.0.0.1", "Device"))
                    .isInstanceOf(UserNotFoundException.class);

            verify(refreshTokenRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("validateRefreshToken")
    class ValidateRefreshToken {

        @Test
        @DisplayName("Valid token returns refresh token")
        void shouldReturnTokenWhenValid() {
            String clearToken = "valid-token";

            RefreshToken token = buildToken(clearToken, null, now().plusHours(1));

            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

            RefreshToken result = refreshTokenService.validateRefreshToken(null, clearToken);

            assertThat(result.getRevokedAt()).isNull();
        }

        @Test
        @DisplayName("Revoked tokens revokes all tokens and throws exception")
        void shouldRevokeAllAndThrowWhenRevoked() {
            String clearToken = "revoked-token";
            RefreshToken token = buildToken(clearToken, now().minusDays(1), now().plusHours(1));

            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

            assertThatThrownBy(() -> refreshTokenService.validateRefreshToken(null, clearToken))
                    .isInstanceOf(TokenRevokedException.class);
        }

        @Test
        @DisplayName("Expired token throws exception")
        void shouldMarkRevokedAndThrowWhenExpired() {
            String clearToken = "expired-token";
            RefreshToken token = buildToken(clearToken, null, now().minusMinutes(5));

            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

            assertThatThrownBy(() -> refreshTokenService.validateRefreshToken(null, clearToken))
                    .isInstanceOf(TokenExpiredException.class);
        }

        @Test
        @DisplayName("Token revokes right now - valid")
        void shouldBeValidWhenExpiresExactlyNow() {
            String clearToken = "edge-token";
            RefreshToken token = buildToken(clearToken, null, now());

            when(refreshTokenRepository
                    .findByTokenHash(TokenHashUtil.hash(clearToken)))
                    .thenReturn(Optional.of(token));

            RefreshToken result = refreshTokenService
                    .validateRefreshToken(null, clearToken);

            assertThat(result).isEqualTo(token);
        }

        @Test
        @DisplayName("TokenNotFoundException")
        void shouldThrowWhenNotFound() {
            when(refreshTokenRepository.findByTokenHash(anyString()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> refreshTokenService
                    .validateRefreshToken(null, "unknown-token"))
                    .isInstanceOf(TokenNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("revokeToken")
    class RevokeToken {

        @Test
        @DisplayName("Existing token gets revoked")
        void shouldRevokeExistingToken() {
            String clearToken = "existing-token";
            RefreshToken token = buildToken(clearToken, null, now().plusHours(1));

            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

            refreshTokenService.revokeToken(clearToken);

            assertThat(token.getRevokedAt()).isEqualTo(now());
            verify(refreshTokenRepository).save(token);
        }

        @Test
        @DisplayName("Non-existing token does not throws or save anything")
        void shouldDoNothingWhenTokenNotFound() {
            when(refreshTokenRepository.findByTokenHash(anyString()))
                    .thenReturn(Optional.empty());

            refreshTokenService.revokeToken("nonexistent");

            verify(refreshTokenRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("revokeAllUserTokens")
    class RevokeAllUserTokens {

        @Test
        @DisplayName("Delegates the call into repository")
        void shouldDelegateToRepository() {
            when(refreshTokenRepository.revokeAllByUserId(1L))
                    .thenReturn(3);

            refreshTokenService.revokeAllUserTokens(1L);

            verify(refreshTokenRepository).revokeAllByUserId(1L);
        }
    }

    @Nested
    @DisplayName("rotateRefreshToken")
    class RotateRefreshToken {

        @Test
        @DisplayName("Returns new token and marks old token as consumed")
        void shouldRotateSuccessfully() {
            String clearToken = "valid-token";
            RefreshToken oldToken = buildToken(clearToken, null, now().plusHours(1));

            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(oldToken));

            String newToken = refreshTokenService.rotateRefreshToken(clearToken, "127.0.0.1", "Device");

            assertThat(newToken).isNotNull();
            assertThat(oldToken.getConsumedAt()).isEqualTo(now());

            verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("Grace Period: successful rotation")
        void shouldAllowRotationWithinGracePeriod() {
            String clearToken = "grace-token";
            RefreshToken oldToken = buildToken(clearToken, null, now().plusHours(1));

            // Grace period 30 secs
            oldToken.setConsumedAt(now().minusSeconds(10));

            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(oldToken));

            String newToken = refreshTokenService.rotateRefreshToken(clearToken, "127.0.0.1", "Device");

            assertThat(newToken).isNotNull();
            verify(refreshTokenRepository, never()).revokeByFamilyId(any());
        }

        @Test
        @DisplayName("Reuse attack: revokes all tokens by family")
        void shouldRevokeFamilyOnReuseAttack() {
            String clearToken = "stolen-token";
            RefreshToken oldToken = buildToken(clearToken, null, now().plusHours(1));
            // Don't match for grace period
            oldToken.setConsumedAt(now().minusMinutes(1));

            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(oldToken));

            assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(clearToken, "127.0.0.1", "Device"))
                    .isInstanceOf(TokenRevokedException.class);

            verify(refreshTokenRepository).revokeByFamilyId(oldToken.getFamilyId());
        }

        @Test
        @DisplayName("Revoked token rotation revokes all tokens by family")
        void shouldRevokeFamilyIfTokenIsAlreadyRevoked() {
            String clearToken = "revoked-token";
            RefreshToken oldToken = buildToken(clearToken, now().minusMinutes(5), now().plusHours(1));

            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(oldToken));

            assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(clearToken, "127.0.0.1", "Device"))
                    .isInstanceOf(TokenRevokedException.class);

            verify(refreshTokenRepository).revokeByFamilyId(oldToken.getFamilyId());
        }
    }
}
