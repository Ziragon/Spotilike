package com.spotilike.userservice.service;

import com.spotilike.userservice.dto.response.AuthResponse;
import com.spotilike.userservice.exception.auth.InvalidCredentialsException;
import com.spotilike.userservice.exception.resource.DuplicateEmailException;
import com.spotilike.userservice.exception.resource.UserNotFoundException;
import com.spotilike.userservice.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .email("test@mail.com")
                .passwordHash("hashedPass")
                .username("nick")
                .build();
    }

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("Успешная регистрация — возвращает токены")
        void shouldReturnTokensOnSuccess() {
            // Given
            when(userService.createUser("test@mail.com", "rawPass", "nick"))
                    .thenReturn(testUser);
            when(jwtService.generateToken(testUser))
                    .thenReturn("access-token");
            when(refreshTokenService.createRefreshToken(1L, "127.0.0.1", "Device"))
                    .thenReturn("refresh-token");

            // When
            AuthResponse response = authService.register(
                    "test@mail.com", "rawPass", "nick", "127.0.0.1", "Device"
            );

            // Then
            assertThat(response.accessToken()).isEqualTo("access-token");
            assertThat(response.refreshToken()).isEqualTo("refresh-token");
        }

        @Test
        @DisplayName("Делегирует создание пользователя в UserService")
        void shouldDelegateUserCreationToUserService() {
            // Given
            when(userService.createUser(any(), any(), any()))
                    .thenReturn(testUser);
            when(jwtService.generateToken(any())).thenReturn("token");
            when(refreshTokenService.createRefreshToken(any(), any(), any()))
                    .thenReturn("refresh");

            // When
            authService.register("test@mail.com", "rawPass", "nick",
                    "127.0.0.1", "Device");

            // Then
            verify(userService).createUser("test@mail.com", "rawPass", "nick");
        }

        @Test
        @DisplayName("Создаёт refresh-токен с правильными ip и device")
        void shouldCreateRefreshTokenWithCorrectIpAndDevice() {
            // Given
            when(userService.createUser(any(), any(), any()))
                    .thenReturn(testUser);
            when(jwtService.generateToken(any())).thenReturn("token");
            when(refreshTokenService.createRefreshToken(any(), any(), any()))
                    .thenReturn("refresh");

            // When
            authService.register("test@mail.com", "rawPass", "nick",
                    "10.0.0.1", "iPhone");

            // Then
            verify(refreshTokenService)
                    .createRefreshToken(1L, "10.0.0.1", "iPhone");
        }

        @Test
        @DisplayName("Пробрасывает исключение от UserService")
        void shouldPropagateExceptionFromUserService() {
            // Given
            when(userService.createUser(any(), any(), any()))
                    .thenThrow(new DuplicateEmailException());

            // When & Then
            assertThatThrownBy(() ->
                    authService.register("test@mail.com", "rawPass", "nick",
                            "127.0.0.1", "Device"))
                    .isInstanceOf(DuplicateEmailException.class);

            // Токены не должны создаваться
            verify(jwtService, never()).generateToken(any());
            verify(refreshTokenService, never())
                    .createRefreshToken(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("Успешный логин - возвращает токены")
        void shouldReturnTokensOnSuccess() {
            // Given
            when(userService.findByEmail("test@mail.com"))
                    .thenReturn(testUser);
            when(passwordEncoder.matches("rawPass", "hashedPass"))
                    .thenReturn(true);
            when(jwtService.generateToken(testUser))
                    .thenReturn("access-token");
            when(refreshTokenService.createRefreshToken(1L, "127.0.0.1", "Device"))
                    .thenReturn("refresh-token");

            // When
            AuthResponse response = authService.login(
                    "test@mail.com", "rawPass", "127.0.0.1", "Device"
            );

            // Then
            assertThat(response.accessToken()).isEqualTo("access-token");
            assertThat(response.refreshToken()).isEqualTo("refresh-token");
        }

        @Test
        @DisplayName("Неверный пароль - InvalidCredentialsException")
        void shouldThrowOnWrongPassword() {
            // Given
            when(userService.findByEmail("test@mail.com"))
                    .thenReturn(testUser);
            when(passwordEncoder.matches("wrongPass", "hashedPass"))
                    .thenReturn(false);

            // When & Then
            assertThatThrownBy(() ->
                    authService.login("test@mail.com", "wrongPass",
                            "127.0.0.1", "Device"))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(jwtService, never()).generateToken(any());
            verify(refreshTokenService, never())
                    .createRefreshToken(any(), any(), any());
        }

        @Test
        @DisplayName("Пользователь не найден - пробрасывает UserNotFoundException")
        void shouldPropagateWhenUserNotFound() {
            // Given
            when(userService.findByEmail("no@mail.com"))
                    .thenThrow(new UserNotFoundException("no@mail.com"));

            // When & Then
            assertThatThrownBy(() ->
                    authService.login("no@mail.com", "pass",
                            "127.0.0.1", "Device"))
                    .isInstanceOf(UserNotFoundException.class);

            verify(passwordEncoder, never()).matches(any(), any());
            verify(jwtService, never()).generateToken(any());
        }

        @Test
        @DisplayName("Создаёт refresh-токен с правильными ip и device")
        void shouldCreateRefreshTokenWithCorrectIpAndDevice() {
            // Given
            when(userService.findByEmail(any())).thenReturn(testUser);
            when(passwordEncoder.matches(any(), any())).thenReturn(true);
            when(jwtService.generateToken(any())).thenReturn("token");
            when(refreshTokenService.createRefreshToken(any(), any(), any()))
                    .thenReturn("refresh");

            // When
            authService.login("test@mail.com", "rawPass", "10.0.0.1", "Android");

            // Then
            verify(refreshTokenService)
                    .createRefreshToken(1L, "10.0.0.1", "Android");
        }
    }
}