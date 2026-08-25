package com.spotilike.userservice.controller;

import com.spotilike.userservice.config.ClockConfig;
import com.spotilike.userservice.config.SecurityConfig;
import com.spotilike.userservice.dto.request.LoginRequest;
import com.spotilike.userservice.dto.request.RegisterRequest;
import com.spotilike.userservice.dto.response.AuthResponse;
import com.spotilike.shared.exception.ErrorResponseFactory;
import com.spotilike.userservice.dto.response.UserDto;
import com.spotilike.userservice.exception.auth.InvalidCredentialsException;
import com.spotilike.userservice.exception.resource.DuplicateEmailException;
import com.spotilike.userservice.exception.resource.UserNotFoundException;
import com.spotilike.userservice.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JacksonAutoConfiguration.class, ClockConfig.class, ErrorResponseFactory.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private AuthService authService;

    private AuthResponse authResponse;

    private static RequestPostProcessor anonymousGatewayHeaders(String headerName, String headerKey) {
        return request -> {
            request.addHeader("X-User-Anonymous", "true");
            request.addHeader(headerName, headerKey);
            return request;
        };
    }

    @Value("${application.security.gateway.header-name}") String headerName;
    @Value("${application.security.gateway.header-key}") String headerKey;

    @BeforeEach
    void setUp() {
        UserDto testUserDto = new UserDto(
                1L,
                "test@mail.com",
                "nick",
                "https://avatar.com/1",
                false,
                Set.of("ROLE_USER"),
                OffsetDateTime.now()
        );

        authResponse = new AuthResponse(
                "access-token",
                "refresh-token",
                900000L,
                testUserDto
        );
    }

    @Nested
    @DisplayName("POST /register")
    class Register {

        @Test
        @DisplayName("201 by a valid request")
        void shouldReturn201WithTokens() throws Exception {
            when(authService.register(any(), any(), any(), any(), any()))
                    .thenReturn(authResponse);

            mockMvc.perform(post("/api/v1/auth/register")
                            .with(anonymousGatewayHeaders(headerName, headerKey))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(
                                    new RegisterRequest("test@mail.com", "pass1234", "nick"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.accessToken").value("access-token"))
                    .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
        }

        @Nested
        @DisplayName("Validation")
        class Validation {

            @Test
            @DisplayName("400 by incorrect email")
            void shouldReturn400OnInvalidEmail() throws Exception {
                mockMvc.perform(post("/api/v1/auth/register")
                                .with(anonymousGatewayHeaders(headerName, headerKey))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(
                                        new RegisterRequest("not-email", "pass1234", "nick"))))
                        .andExpect(status().isBadRequest());
            }

            @Test
            @DisplayName("400 by empty password")
            void shouldReturn400OnBlankPassword() throws Exception {
                mockMvc.perform(post("/api/v1/auth/register")
                                .with(anonymousGatewayHeaders(headerName, headerKey))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(
                                        new RegisterRequest("test@mail.com", "", "nick"))))
                        .andExpect(status().isBadRequest());
            }

            @Test
            @DisplayName("400 by empty username")
            void shouldReturn400OnBlankUsername() throws Exception {
                mockMvc.perform(post("/api/v1/auth/register")
                                .with(anonymousGatewayHeaders(headerName, headerKey))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(
                                        new RegisterRequest("test@mail.com", "pass123", ""))))
                        .andExpect(status().isBadRequest());
            }
        }

        @Nested
        @DisplayName("Exceptions")
        class Exceptions {

            @Test
            @DisplayName("409 by duplicate email")
            void shouldReturn409OnDuplicateEmail() throws Exception {
                when(authService.register(any(), any(), any(), any(), any()))
                        .thenThrow(new DuplicateEmailException());

                mockMvc.perform(post("/api/v1/auth/register")
                                .with(anonymousGatewayHeaders(headerName, headerKey))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(
                                        new RegisterRequest("test@mail.com", "pass1234", "nick"))))
                        .andExpect(status().isConflict());
            }
        }
    }

    @Nested
    @DisplayName("POST /login")
    class Login {

        @Test
        @DisplayName("200 by valid request")
        void shouldReturn200WithTokens() throws Exception {
            when(authService.login(any(), any(), any(), any()))
                    .thenReturn(authResponse);

            mockMvc.perform(post("/api/v1/auth/login")
                            .with(anonymousGatewayHeaders(headerName, headerKey))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(
                                    new LoginRequest("test@mail.com", "pass123"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("access-token"))
                    .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
        }

        @Nested
        @DisplayName("Validation")
        class Validation {

            @Test
            @DisplayName("400 by incorrect email")
            void shouldReturn400OnInvalidEmail() throws Exception {
                mockMvc.perform(post("/api/v1/auth/login")
                                .with(anonymousGatewayHeaders(headerName, headerKey))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(
                                        new LoginRequest("not-email", "pass123"))))
                        .andExpect(status().isBadRequest());
            }

            @Test
            @DisplayName("400 by empty password")
            void shouldReturn400OnBlankPassword() throws Exception {
                mockMvc.perform(post("/api/v1/auth/login")
                                .with(anonymousGatewayHeaders(headerName, headerKey))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(
                                        new LoginRequest("test@mail.com", ""))))
                        .andExpect(status().isBadRequest());
            }
        }

        @Nested
        @DisplayName("Exceptions")
        class Exceptions {

            @Test
            @DisplayName("401 by incorrect credentials")
            void shouldReturn401OnInvalidCredentials() throws Exception {
                when(authService.login(any(), any(), any(), any()))
                        .thenThrow(new InvalidCredentialsException());

                mockMvc.perform(post("/api/v1/auth/login")
                                .with(anonymousGatewayHeaders(headerName, headerKey))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(
                                        new LoginRequest("test@mail.com", "wrong"))))
                        .andExpect(status().isUnauthorized());
            }

            @Test
            @DisplayName("404 by user not found")
            void shouldReturn404WhenUserNotFound() throws Exception {
                when(authService.login(any(), any(), any(), any()))
                        .thenThrow(new UserNotFoundException("test@mail.com"));

                mockMvc.perform(post("/api/v1/auth/login")
                                .with(anonymousGatewayHeaders(headerName, headerKey))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(
                                        new LoginRequest("test@mail.com", "pass"))))
                        .andExpect(status().isNotFound());
            }
        }
    }
}