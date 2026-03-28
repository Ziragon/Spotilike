package com.spotilike.userservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotilike.userservice.config.SecurityConfig;
import com.spotilike.userservice.dto.request.LoginRequest;
import com.spotilike.userservice.dto.request.RegisterRequest;
import com.spotilike.userservice.dto.response.AuthResponse;
import com.spotilike.userservice.exception.auth.InvalidCredentialsException;
import com.spotilike.userservice.exception.conflict.DuplicateEmailException;
import com.spotilike.userservice.exception.notfound.UserNotFoundException;
import com.spotilike.userservice.security.HeaderAuthenticationFilter;
import com.spotilike.userservice.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, HeaderAuthenticationFilter.class, JacksonAutoConfiguration.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    private AuthResponse authResponse;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        authResponse = new AuthResponse("access-token", "refresh-token");
    }

    @Nested
    @DisplayName("POST /register")
    class Register {

        @Test
        @DisplayName("201 и токены при валидном запросе")
        void shouldReturn201WithTokens() throws Exception {
            when(authService.register(any(), any(), any(), any(), any()))
                    .thenReturn(authResponse);

            mockMvc.perform(post("/api/v1/auth/register")
                            .header("X-User-Anonymous", "true")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new RegisterRequest("test@mail.com", "pass1234", "nick"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.accessToken").value("access-token"))
                    .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
        }

        @Nested
        @DisplayName("Валидация")
        class Validation {

            @Test
            @DisplayName("400 если email невалидный")
            void shouldReturn400OnInvalidEmail() throws Exception {
                mockMvc.perform(post("/api/v1/auth/register")
                                .header("X-User-Anonymous", "true")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        new RegisterRequest("not-email", "pass1234", "nick"))))
                        .andExpect(status().isBadRequest());
            }

            @Test
            @DisplayName("400 если password пустой")
            void shouldReturn400OnBlankPassword() throws Exception {
                mockMvc.perform(post("/api/v1/auth/register")
                                .header("X-User-Anonymous", "true")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        new RegisterRequest("test@mail.com", "", "nick"))))
                        .andExpect(status().isBadRequest());
            }

            @Test
            @DisplayName("400 если username пустой")
            void shouldReturn400OnBlankUsername() throws Exception {
                mockMvc.perform(post("/api/v1/auth/register")
                                .header("X-User-Anonymous", "true")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        new RegisterRequest("test@mail.com", "pass123", ""))))
                        .andExpect(status().isBadRequest());
            }
        }

        @Nested
        @DisplayName("Исключения")
        class Exceptions {

            @Test
            @DisplayName("409 при дублировании email")
            void shouldReturn409OnDuplicateEmail() throws Exception {
                when(authService.register(any(), any(), any(), any(), any()))
                        .thenThrow(new DuplicateEmailException("test@mail.com"));

                mockMvc.perform(post("/api/v1/auth/register")
                                .header("X-User-Anonymous", "true")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        new RegisterRequest("test@mail.com", "pass1234", "nick"))))
                        .andExpect(status().isConflict());
            }
        }
    }

    @Nested
    @DisplayName("POST /login")
    class Login {

        @Test
        @DisplayName("200 и токены при валидном запросе")
        void shouldReturn200WithTokens() throws Exception {
            when(authService.login(any(), any(), any(), any()))
                    .thenReturn(authResponse);

            mockMvc.perform(post("/api/v1/auth/login")
                            .header("X-User-Anonymous", "true")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new LoginRequest("test@mail.com", "pass123"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("access-token"))
                    .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
        }

        @Nested
        @DisplayName("Валидация")
        class Validation {

            @Test
            @DisplayName("400 если email невалидный")
            void shouldReturn400OnInvalidEmail() throws Exception {
                mockMvc.perform(post("/api/v1/auth/login")
                                .header("X-User-Anonymous", "true")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        new LoginRequest("not-email", "pass123"))))
                        .andExpect(status().isBadRequest());
            }

            @Test
            @DisplayName("400 если password пустой")
            void shouldReturn400OnBlankPassword() throws Exception {
                mockMvc.perform(post("/api/v1/auth/login")
                                .header("X-User-Anonymous", "true")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        new LoginRequest("test@mail.com", ""))))
                        .andExpect(status().isBadRequest());
            }
        }

        @Nested
        @DisplayName("Исключения")
        class Exceptions {

            @Test
            @DisplayName("401 при неверных credentials")
            void shouldReturn401OnInvalidCredentials() throws Exception {
                when(authService.login(any(), any(), any(), any()))
                        .thenThrow(new InvalidCredentialsException());

                mockMvc.perform(post("/api/v1/auth/login")
                                .header("X-User-Anonymous", "true")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        new LoginRequest("test@mail.com", "wrong"))))
                        .andExpect(status().isUnauthorized());
            }

            @Test
            @DisplayName("404 если пользователь не найден")
            void shouldReturn404WhenUserNotFound() throws Exception {
                when(authService.login(any(), any(), any(), any()))
                        .thenThrow(new UserNotFoundException("test@mail.com"));

                mockMvc.perform(post("/api/v1/auth/login")
                                .header("X-User-Anonymous", "true")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        new LoginRequest("test@mail.com", "pass"))))
                        .andExpect(status().isNotFound());
            }
        }
    }
}