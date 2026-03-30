package com.spotilike.userservice.security;

import com.spotilike.shared.exception.ErrorResponseFactory;
import com.spotilike.userservice.config.ClockConfig;
import com.spotilike.userservice.config.SecurityConfig;
import com.spotilike.userservice.controller.AuthController;
import com.spotilike.userservice.service.AuthService;
import com.spotilike.userservice.service.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, ClockConfig.class, ErrorResponseFactory.class})
class HeaderAuthenticationFilterIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    @DisplayName("Access denied (Without Gateway headers)")
    void smoke_shouldRejectDirectAccess() throws Exception {
        mockMvc.perform(get("/api/v1/auth/test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Valid headers and user mapping")
    void smoke_shouldAuthenticateViaHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/auth/test")
                        .header("X-User-Anonymous", "false")
                        .header("X-User-Id", "42")
                        .header("X-User-Email", "smoke@test.ru")
                        .header("X-User-Roles", "ROLE_USER"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello, you're auth user: smoke@test.ru"));
    }
}