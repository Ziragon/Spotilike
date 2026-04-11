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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant FIXED_INSTANT =
            Instant.parse("2025-01-15T12:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final long REFRESH_EXPIRATION_MS = 604_800_000L; // 7 дней

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User testUser;

    @BeforeEach
    void setUp() {
        // Фиксированные часы
        Clock fixedClock = Clock.fixed(FIXED_INSTANT, ZONE);
        ReflectionTestUtils.setField(refreshTokenService, "clock", fixedClock);
        ReflectionTestUtils.setField(refreshTokenService,
                "refreshExpiration", REFRESH_EXPIRATION_MS);

        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@mail.com")
                .build();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(FIXED_INSTANT, ZONE);
    }

    private RefreshToken buildToken(String clearToken, OffsetDateTime revokedAt,
                                    OffsetDateTime expiresAt) {
        return RefreshToken.builder()
                .id(1L)
                .user(testUser)
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
        @DisplayName("Отзывает старые токены на том же устройстве")
        void shouldRevokeOldTokensOnSameDevice() {
            // Given
            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(testUser));
            when(refreshTokenRepository
                    .revokeByUserIdAndDeviceInfo(1L, "Same-Device"))
                    .thenReturn(1);

            // When
            refreshTokenService
                    .createRefreshToken(1L, "127.0.0.1", "Same-Device");

            // Then
            verify(refreshTokenRepository)
                    .revokeByUserIdAndDeviceInfo(1L, "Same-Device");
        }

        @Test
        @DisplayName("Бросает UserNotFoundException для несуществующего пользователя")
        void shouldThrowWhenUserNotFound() {
            // Given
            when(userRepository.findById(999L))
                    .thenReturn(Optional.empty());

            // When & Then
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

            RefreshToken result = refreshTokenService.validateRefreshToken(clearToken);

            assertThat(result.getRevokedAt()).isNull();
        }

        @Test
        @DisplayName("Revoked tokens revokes all tokens and throws exception")
        void shouldRevokeAllAndThrowWhenRevoked() {
            String clearToken = "revoked-token";
            RefreshToken token = buildToken(clearToken, now().minusDays(1), now().plusHours(1));

            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

            assertThatThrownBy(() -> refreshTokenService.validateRefreshToken(clearToken))
                    .isInstanceOf(TokenRevokedException.class);

            verify(refreshTokenRepository).revokeAllByUserId(1L);
        }

        @Test
        @DisplayName("Expired token set revokedAt and throws exception")
        void shouldMarkRevokedAndThrowWhenExpired() {
            String clearToken = "expired-token";
            RefreshToken token = buildToken(clearToken, null, now().minusMinutes(5));

            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

            assertThatThrownBy(() -> refreshTokenService.validateRefreshToken(clearToken))
                    .isInstanceOf(TokenExpiredException.class);

            // Теперь проверяем, что дата отзыва проставилась
            assertThat(token.getRevokedAt()).isNotNull();
            verify(refreshTokenRepository).save(token);
        }

        @Test
        @DisplayName("Token revokes right now - valid")
        void shouldBeValidWhenExpiresExactlyNow() {
            // Given
            String clearToken = "edge-token";
            RefreshToken token = buildToken(clearToken, null, now());

            when(refreshTokenRepository
                    .findByTokenHash(TokenHashUtil.hash(clearToken)))
                    .thenReturn(Optional.of(token));

            // When
            RefreshToken result = refreshTokenService
                    .validateRefreshToken(clearToken);

            // Then
            assertThat(result).isEqualTo(token);
        }

        @Test
        @DisplayName("TokenNotFoundException")
        void shouldThrowWhenNotFound() {
            // Given
            when(refreshTokenRepository.findByTokenHash(anyString()))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> refreshTokenService
                    .validateRefreshToken("unknown-token"))
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
        @DisplayName("Несуществующий токен - не падает, не сохраняет")
        void shouldDoNothingWhenTokenNotFound() {
            // Given
            when(refreshTokenRepository.findByTokenHash(anyString()))
                    .thenReturn(Optional.empty());

            // When
            refreshTokenService.revokeToken("nonexistent");

            // Then
            verify(refreshTokenRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("revokeAllUserTokens")
    class RevokeAllUserTokens {

        @Test
        @DisplayName("Делегирует вызов в репозиторий")
        void shouldDelegateToRepository() {
            // Given
            when(refreshTokenRepository.revokeAllByUserId(1L))
                    .thenReturn(3);

            // When
            refreshTokenService.revokeAllUserTokens(1L);

            // Then
            verify(refreshTokenRepository).revokeAllByUserId(1L);
        }
    }
}