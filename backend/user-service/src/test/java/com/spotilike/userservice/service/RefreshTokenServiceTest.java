package com.spotilike.userservice.service;

import com.spotilike.userservice.exception.auth.TokenExpiredException;
import com.spotilike.userservice.exception.auth.TokenRevokedException;
import com.spotilike.userservice.exception.notfound.TokenNotFoundException;
import com.spotilike.userservice.exception.notfound.UserNotFoundException;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(FIXED_INSTANT, ZONE);
    }

    private RefreshToken buildToken(String clearToken, boolean revoked,
                                    LocalDateTime expiresAt) {
        return RefreshToken.builder()
                .id(1L)
                .user(testUser)
                .tokenHash(TokenHashUtil.hash(clearToken))
                .ipAddress("127.0.0.1")
                .deviceInfo("Test-Device")
                .revoked(revoked)
                .expiresAt(expiresAt)
                .build();
    }

    @Nested
    @DisplayName("createRefreshToken")
    class CreateRefreshToken {

        @Test
        @DisplayName("Создаёт токен и сохраняет в репозиторий")
        void shouldCreateAndSave() {
            // Given
            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(testUser));
            when(refreshTokenRepository
                    .revokeByUserIdAndDeviceInfo(1L, "Device"))
                    .thenReturn(0);

            // When
            String clearToken = refreshTokenService
                    .createRefreshToken(1L, "127.0.0.1", "Device");

            // Then
            assertThat(clearToken).isNotBlank();

            ArgumentCaptor<RefreshToken> captor =
                    ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository).save(captor.capture());

            RefreshToken saved = captor.getValue();
            assertThat(saved.getUser()).isEqualTo(testUser);
            assertThat(saved.getTokenHash())
                    .isEqualTo(TokenHashUtil.hash(clearToken));
            assertThat(saved.getIpAddress()).isEqualTo("127.0.0.1");
            assertThat(saved.getDeviceInfo()).isEqualTo("Device");
            assertThat(saved.isRevoked()).isFalse();
            assertThat(saved.getExpiresAt())
                    .isEqualTo(now().plusNanos(
                            REFRESH_EXPIRATION_MS * 1_000_000));
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
        @DisplayName("Валидный токен — возвращает RefreshToken")
        void shouldReturnTokenWhenValid() {
            // Given
            String clearToken = "valid-token";
            RefreshToken token = buildToken(
                    clearToken, false, now().plusHours(1));

            when(refreshTokenRepository
                    .findByTokenHash(TokenHashUtil.hash(clearToken)))
                    .thenReturn(Optional.of(token));

            // When
            RefreshToken result = refreshTokenService
                    .validateRefreshToken(clearToken);

            // Then
            assertThat(result).isEqualTo(token);
            assertThat(result.isRevoked()).isFalse();
            verify(refreshTokenRepository, never()).save(any());
            verify(refreshTokenRepository, never())
                    .revokeAllByUserId(anyLong());
        }

        @Test
        @DisplayName("Отозванный токен — отзывает ВСЕ токены и бросает исключение")
        void shouldRevokeAllAndThrowWhenRevoked() {
            // Given
            String clearToken = "revoked-token";
            RefreshToken token = buildToken(
                    clearToken, true, now().plusHours(1));

            when(refreshTokenRepository
                    .findByTokenHash(TokenHashUtil.hash(clearToken)))
                    .thenReturn(Optional.of(token));

            // When & Then
            assertThatThrownBy(() -> refreshTokenService
                    .validateRefreshToken(clearToken))
                    .isInstanceOf(TokenRevokedException.class);

            verify(refreshTokenRepository).revokeAllByUserId(1L);
        }

        @Test
        @DisplayName("Просроченный токен — помечает revoked и бросает исключение")
        void shouldMarkRevokedAndThrowWhenExpired() {
            // Given
            String clearToken = "expired-token";
            // Истёк 5 минут назад
            RefreshToken token = buildToken(
                    clearToken, false, now().minusMinutes(5));

            when(refreshTokenRepository
                    .findByTokenHash(TokenHashUtil.hash(clearToken)))
                    .thenReturn(Optional.of(token));

            // When & Then
            assertThatThrownBy(() -> refreshTokenService
                    .validateRefreshToken(clearToken))
                    .isInstanceOf(TokenExpiredException.class);

            assertThat(token.isRevoked()).isTrue();
            verify(refreshTokenRepository).save(token);
            // НЕ должен отзывать все токены — это не security-инцидент
            verify(refreshTokenRepository, never())
                    .revokeAllByUserId(anyLong());
        }

        @Test
        @DisplayName("Токен истекает ровно сейчас - ещё валиден (isBefore)")
        void shouldBeValidWhenExpiresExactlyNow() {
            // Given
            String clearToken = "edge-token";
            RefreshToken token = buildToken(clearToken, false, now());

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
        @DisplayName("Несуществующий токен - TokenNotFoundException")
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
        @DisplayName("Существующий токен - помечает revoked")
        void shouldRevokeExistingToken() {
            // Given
            String clearToken = "existing-token";
            RefreshToken token = buildToken(
                    clearToken, false, now().plusHours(1));

            when(refreshTokenRepository
                    .findByTokenHash(TokenHashUtil.hash(clearToken)))
                    .thenReturn(Optional.of(token));

            // When
            refreshTokenService.revokeToken(clearToken);

            // Then
            assertThat(token.isRevoked()).isTrue();
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