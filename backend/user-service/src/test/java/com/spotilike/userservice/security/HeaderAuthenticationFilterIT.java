package com.spotilike.userservice.security;

import com.spotilike.userservice.BaseWebTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HeaderAuthenticationFilterIT extends BaseWebTest {

    @Test
    @DisplayName("401 если заголовок X-User-Anonymous отсутствует")
    void shouldReturn401WhenAnonymousHeaderMissing() throws Exception {
        mockMvc.perform(get("/api/v1/auth/test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("200 для анонимного пользователя через Gateway")
    void shouldAllowAnonymousRequestFromGateway() throws Exception {
        mockMvc.perform(get("/api/v1/auth/test")
                        .header("X-User-Anonymous", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello, you're guest"));
    }

    @Test
    @DisplayName("200 при наличии всех валидных заголовков")
    void shouldAuthenticateWhenAllHeadersPresent() throws Exception {
        mockMvc.perform(get("/api/v1/auth/test")
                        .header("X-User-Anonymous", "false")
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "test@crmespo.ru")
                        .header("X-User-Roles", "ROLE_USER"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello, you're auth user: test@crmespo.ru"));
    }

    @Test
    @DisplayName("401 если X-User-Anonymous=false, но отсутствуют данные пользователя")
    void shouldReturn401WhenUserHeadersMissing() throws Exception {
        mockMvc.perform(get("/api/v1/auth/test")
                        .header("X-User-Anonymous", "false"))
                .andExpect(status().isUnauthorized());
    }
}