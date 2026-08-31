package com.spotilike.userservice.service;

import com.spotilike.userservice.dto.response.AuthResponse;
import com.spotilike.userservice.exception.auth.InvalidCredentialsException;
import com.spotilike.userservice.exception.resource.DuplicateEmailException;
import com.spotilike.userservice.exception.resource.UserNotFoundException;
import com.spotilike.userservice.model.RefreshToken;
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

import java.util.Optional;

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
        @DisplayName("Successful registration returns auth response")
        void shouldReturnTokensOnSuccess() {
            when(userService.createUser("test@mail.com", "rawPass", "nick"))
                    .thenReturn(testUser);
            when(jwtService.generateToken(testUser))
                    .thenReturn("access-token");
            when(refreshTokenService.createRefreshToken(1L, "127.0.0.1", "Device"))
                    .thenReturn("refresh-token");

            AuthResponse response = authService.register(
                    "test@mail.com", "rawPass", "nick", "127.0.0.1", "Device"
            );

            assertThat(response.accessToken()).isEqualTo("access-token");
            assertThat(response.refreshToken()).isEqualTo("refresh-token");
        }

        @Test
        @DisplayName("Delegates user creation in UserService")
        void shouldDelegateUserCreationToUserService() {
            when(userService.createUser(any(), any(), any()))
                    .thenReturn(testUser);
            when(jwtService.generateToken(any())).thenReturn("token");
            when(refreshTokenService.createRefreshToken(any(), any(), any()))
                    .thenReturn("refresh");

            authService.register("test@mail.com", "rawPass", "nick",
                    "127.0.0.1", "Device");

            // Then
            verify(userService).createUser("test@mail.com", "rawPass", "nick");
        }

        @Test
        @DisplayName("Creates refresh-token with correct ip and device")
        void shouldCreateRefreshTokenWithCorrectIpAndDevice() {
            when(userService.createUser(any(), any(), any()))
                    .thenReturn(testUser);
            when(jwtService.generateToken(any())).thenReturn("token");
            when(refreshTokenService.createRefreshToken(any(), any(), any()))
                    .thenReturn("refresh");

            authService.register("test@mail.com", "rawPass", "nick",
                    "10.0.0.1", "iPhone");

            verify(refreshTokenService)
                    .createRefreshToken(1L, "10.0.0.1", "iPhone");
        }

        @Test
        @DisplayName("Throws exception from UserService")
        void shouldPropagateExceptionFromUserService() {
            when(userService.createUser(any(), any(), any()))
                    .thenThrow(new DuplicateEmailException());

            assertThatThrownBy(() ->
                    authService.register("test@mail.com", "rawPass", "nick",
                            "127.0.0.1", "Device"))
                    .isInstanceOf(DuplicateEmailException.class);

            verify(jwtService, never()).generateToken(any());
            verify(refreshTokenService, never())
                    .createRefreshToken(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("Successful login returns auth response")
        void shouldReturnTokensOnSuccess() {
            when(userService.findByEmail("test@mail.com"))
                    .thenReturn(testUser);
            when(passwordEncoder.matches("rawPass", "hashedPass"))
                    .thenReturn(true);
            when(jwtService.generateToken(testUser))
                    .thenReturn("access-token");
            when(refreshTokenService.createRefreshToken(1L, "127.0.0.1", "Device"))
                    .thenReturn("refresh-token");

            AuthResponse response = authService.login(
                    "test@mail.com", "rawPass", "127.0.0.1", "Device"
            );

            assertThat(response.accessToken()).isEqualTo("access-token");
            assertThat(response.refreshToken()).isEqualTo("refresh-token");
        }

        @Test
        @DisplayName("Incorrect password - InvalidCredentialsException")
        void shouldThrowOnWrongPassword() {
            when(userService.findByEmail("test@mail.com"))
                    .thenReturn(testUser);
            when(passwordEncoder.matches("wrongPass", "hashedPass"))
                    .thenReturn(false);

            assertThatThrownBy(() ->
                    authService.login("test@mail.com", "wrongPass",
                            "127.0.0.1", "Device"))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(jwtService, never()).generateToken(any());
            verify(refreshTokenService, never())
                    .createRefreshToken(any(), any(), any());
        }

        @Test
        @DisplayName("User not found - UserNotFoundException")
        void shouldPropagateWhenUserNotFound() {
            when(userService.findByEmail("no@mail.com"))
                    .thenThrow(new UserNotFoundException("no@mail.com"));

            assertThatThrownBy(() ->
                    authService.login("no@mail.com", "pass",
                            "127.0.0.1", "Device"))
                    .isInstanceOf(UserNotFoundException.class);

            verify(passwordEncoder, never()).matches(any(), any());
            verify(jwtService, never()).generateToken(any());
        }

        @Test
        @DisplayName("Creates refresh-token with correct ip and device")
        void shouldCreateRefreshTokenWithCorrectIpAndDevice() {
            when(userService.findByEmail(any())).thenReturn(testUser);
            when(passwordEncoder.matches(any(), any())).thenReturn(true);
            when(jwtService.generateToken(any())).thenReturn("token");
            when(refreshTokenService.createRefreshToken(any(), any(), any()))
                    .thenReturn("refresh");

            authService.login("test@mail.com", "rawPass", "10.0.0.1", "Android");

            verify(refreshTokenService)
                    .createRefreshToken(1L, "10.0.0.1", "Android");
        }
    }

    @Nested
    @DisplayName("refreshToken")
    class RefreshTokenEndpoint {

        @Test
        @DisplayName("Successful token rotate")
        void shouldReturnNewTokensOnSuccess() {
            String oldRefresh = "old-refresh";
            String newRefresh = "new-refresh";
            String newAccess = "new-access";

            RefreshToken mockTokenEntity = RefreshToken.builder().user(testUser).build();

            when(refreshTokenService.rotateRefreshToken(oldRefresh, "127.0.0.1", "Device"))
                    .thenReturn(newRefresh);
            when(refreshTokenService.findByToken(oldRefresh))
                    .thenReturn(Optional.of(mockTokenEntity));
            when(jwtService.generateToken(testUser))
                    .thenReturn(newAccess);

            AuthResponse response = authService.refreshToken(oldRefresh, "127.0.0.1", "Device");

            assertThat(response.accessToken()).isEqualTo(newAccess);
            assertThat(response.refreshToken()).isEqualTo(newRefresh);
        }
    }
}
