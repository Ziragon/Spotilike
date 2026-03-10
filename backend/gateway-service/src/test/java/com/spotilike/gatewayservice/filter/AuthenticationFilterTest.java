package com.spotilike.gatewayservice.filter;

import com.spotilike.gatewayservice.config.AppGatewayProperties;
import com.spotilike.gatewayservice.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private GatewayFilterChain chain;

    private AuthenticationFilter filter;

    private static final String VALID_TOKEN = "valid.jwt.token";
    private static final String BEARER_TOKEN = "Bearer " + VALID_TOKEN;

    @BeforeEach
    void setUp() {
        AppGatewayProperties gatewayProperties = new AppGatewayProperties();
        gatewayProperties.setOpenPaths(List.of(
                "/api/v1/auth/**",
                "/actuator/health"
        ));

        filter = new AuthenticationFilter(jwtUtil, gatewayProperties);
    }

    // -- Хелперы --

    private MockServerWebExchange exchangeWithPath(String path) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get(path).build()
        );
    }

    private MockServerWebExchange exchangeWithAuth(String authHeader) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .build()
        );
    }

    private MockServerWebExchange exchangeWithSpoofedHeaders() {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/login")
                        .header("X-User-Id", "999")
                        .header("X-User-Email", "hacker@evil.com")
                        .header("X-User-Roles", "ROLE_ADMIN")
                        .header("X-User-Anonymous", "false")
                        .build()
        );
    }

    private Claims mockClaims(Integer userId, String email, List<String> roles) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(email);
        when(claims.get("userId", Integer.class)).thenReturn(userId);
        lenient().when(claims.get("roles", List.class)).thenReturn(roles);

        return claims;
    }

    private void mockValidToken(Integer userId, String email, List<String> roles) {
        when(jwtUtil.isValid(VALID_TOKEN)).thenReturn(true);

        Claims claims = mockClaims(userId, email, roles);
        when(jwtUtil.getAllClaimsFromToken(VALID_TOKEN)).thenReturn(claims);
    }

    private ServerWebExchange captureDownstreamExchange() {
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        return captor.getValue();
    }

    // -- Тесты --

    @Nested
    @DisplayName("Open paths")
    class OpenPaths {

        @Test
        @DisplayName("Открытый эндпоинт без токена (Anonymous = true)")
        void openPath_passesThrough() {
            MockServerWebExchange exchange = exchangeWithPath("/api/v1/auth/login");
            when(chain.filter(any())).thenReturn(Mono.empty());

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            ServerWebExchange downstream = captureDownstreamExchange();
            HttpHeaders headers = downstream.getRequest().getHeaders();

            assertThat(headers.getFirst("X-User-Anonymous")).isEqualTo("true");
            assertThat(headers.containsHeader("X-User-Id")).isFalse();
            assertThat(headers.containsHeader("X-User-Email")).isFalse();
            assertThat(headers.containsHeader("X-User-Roles")).isFalse();
        }

        @Test
        @DisplayName("Открытый эндпоинт с невалидным токеном (Anonymous = true)")
        void openPath_invalidToken_setsAnonymous() {
            when(jwtUtil.isValid(VALID_TOKEN)).thenReturn(false);
            when(chain.filter(any())).thenReturn(Mono.empty());

            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/auth/login")
                            .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                            .build()
            );

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            ServerWebExchange downstream = captureDownstreamExchange();
            assertThat(downstream.getRequest().getHeaders().getFirst("X-User-Anonymous"))
                    .isEqualTo("true");
        }

        @Test
        @DisplayName("Открытый эндпоинт с валидным токеном (Anonymous = false)")
        void openPath_validToken_setsUserHeaders() {
            mockValidToken(42, "user@test.com", List.of("ROLE_USER"));
            when(chain.filter(any())).thenReturn(Mono.empty());

            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/auth/login")
                            .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                            .build()
            );

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            ServerWebExchange downstream = captureDownstreamExchange();
            HttpHeaders headers = downstream.getRequest().getHeaders();

            assertThat(headers.getFirst("X-User-Anonymous")).isEqualTo("false");
            assertThat(headers.getFirst("X-User-Id")).isEqualTo("42");
            assertThat(headers.getFirst("X-User-Email")).isEqualTo("user@test.com");
            assertThat(headers.getFirst("X-User-Roles")).isEqualTo("ROLE_USER");
        }

        @Test
        @DisplayName("Открытый эндпоинт (удаляет spoofed заголовки)")
        void openPath_stripsSpoofedHeaders() {
            MockServerWebExchange exchange = exchangeWithSpoofedHeaders();
            when(chain.filter(any())).thenReturn(Mono.empty());

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            ServerWebExchange downstream = captureDownstreamExchange();
            HttpHeaders headers = downstream.getRequest().getHeaders();

            assertThat(headers.containsHeader("X-User-Id")).isFalse();
            assertThat(headers.containsHeader("X-User-Email")).isFalse();
            assertThat(headers.containsHeader("X-User-Roles")).isFalse();
        }

        @Test
        @DisplayName("Открытый эндпоинт (Удаление Auth header)")
        void openPath_authorizationRemoved() {
            when(jwtUtil.isValid(VALID_TOKEN)).thenReturn(false);
            when(chain.filter(any())).thenReturn(Mono.empty());

            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/auth/login")
                            .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                            .build()
            );

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            ServerWebExchange downstream = captureDownstreamExchange();
            assertThat(downstream.getRequest().getHeaders().containsHeader(HttpHeaders.AUTHORIZATION))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("Missing / invalid Authorization header")
    class MissingAuth {

        @Test
        @DisplayName("Нет Authorization header (401)")
        void noAuthHeader_returns401() {
            MockServerWebExchange exchange = exchangeWithPath("/api/v1/users/me");

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(exchange.getResponse().getHeaders().getContentType())
                    .isEqualTo(MediaType.APPLICATION_JSON);
            verify(chain, never()).filter(any());
        }

        @Test
        @DisplayName("Authorization без Bearer (401)")
        void noBearerPrefix_returns401() {
            MockServerWebExchange exchange = exchangeWithAuth("Basic abc123");

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(chain, never()).filter(any());
        }
    }

    @Nested
    @DisplayName("Invalid JWT")
    class InvalidJwt {

        @Test
        @DisplayName("Невалидный токен (401)")
        void invalidToken_returns401() {
            when(jwtUtil.isValid(VALID_TOKEN)).thenReturn(false);

            MockServerWebExchange exchange = exchangeWithAuth(BEARER_TOKEN);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(chain, never()).filter(any());
        }
    }

    @Nested
    @DisplayName("Missing claims")
    class MissingClaims {

        @Test
        @DisplayName("Токен без userId (401)")
        void missingUserId_returns401() {
            mockValidToken(null, "user@test.com", List.of("ROLE_USER"));

            MockServerWebExchange exchange = exchangeWithAuth(BEARER_TOKEN);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(chain, never()).filter(any());
        }

        @Test
        @DisplayName("Токен без email (subject) (401)")
        void missingEmail_returns401() {
            mockValidToken(1, null, List.of("ROLE_USER"));

            MockServerWebExchange exchange = exchangeWithAuth(BEARER_TOKEN);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(chain, never()).filter(any());
        }
    }

    @Nested
    @DisplayName("Valid token")
    class ValidToken {

        @Test
        @DisplayName("Прокидывает X-User-Id")
        void setsUserIdHeader() {
            mockValidToken(42, "user@test.com", List.of("ROLE_USER"));
            when(chain.filter(any())).thenReturn(Mono.empty());

            MockServerWebExchange exchange = exchangeWithAuth(BEARER_TOKEN);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            ServerWebExchange downstream = captureDownstreamExchange();
            assertThat(downstream.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("42");
        }

        @Test
        @DisplayName("Прокидывает X-User-Email")
        void setsUserEmailHeader() {
            mockValidToken(1, "admin@test.com", List.of("ROLE_ADMIN"));
            when(chain.filter(any())).thenReturn(Mono.empty());

            MockServerWebExchange exchange = exchangeWithAuth(BEARER_TOKEN);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            ServerWebExchange downstream = captureDownstreamExchange();
            assertThat(downstream.getRequest().getHeaders().getFirst("X-User-Email"))
                    .isEqualTo("admin@test.com");
        }

        @Test
        @DisplayName("Прокидывает X-User-Roles (несколько ролей)")
        void setsUserRolesHeader() {
            mockValidToken(1, "user@test.com", List.of("ROLE_ADMIN", "ROLE_USER"));
            when(chain.filter(any())).thenReturn(Mono.empty());

            MockServerWebExchange exchange = exchangeWithAuth(BEARER_TOKEN);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            ServerWebExchange downstream = captureDownstreamExchange();
            assertThat(downstream.getRequest().getHeaders().getFirst("X-User-Roles"))
                    .isEqualTo("ROLE_ADMIN,ROLE_USER");
        }

        @Test
        @DisplayName("Roles = null (пустая строка)")
        void nullRoles_setsEmptyHeader() {
            mockValidToken(1, "user@test.com", null);
            when(chain.filter(any())).thenReturn(Mono.empty());

            MockServerWebExchange exchange = exchangeWithAuth(BEARER_TOKEN);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            ServerWebExchange downstream = captureDownstreamExchange();
            assertThat(downstream.getRequest().getHeaders().getFirst("X-User-Roles")).isEmpty();
        }

        @Test
        @DisplayName("Удаляет Authorization хедер из downstream-запроса")
        void removesAuthorizationHeader() {
            mockValidToken(1, "user@test.com", List.of("ROLE_USER"));
            when(chain.filter(any())).thenReturn(Mono.empty());

            MockServerWebExchange exchange = exchangeWithAuth(BEARER_TOKEN);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            ServerWebExchange downstream = captureDownstreamExchange();
            assertThat(downstream.getRequest().getHeaders().containsHeader(HttpHeaders.AUTHORIZATION))
                    .isFalse();
        }

        @Test
        @DisplayName("Удаляет spoofed хедеры и заменяет из токена")
        void replacesSpoofedHeaders() {
            mockValidToken(1, "real@test.com", List.of("ROLE_USER"));
            when(chain.filter(any())).thenReturn(Mono.empty());

            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/users/me")
                            .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN)
                            .header("X-User-Id", "999")
                            .header("X-User-Email", "hacker@evil.com")
                            .header("X-User-Roles", "ROLE_ADMIN")
                            .header("X-User-Anonymous", "true")
                            .build()
            );

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            ServerWebExchange downstream = captureDownstreamExchange();
            HttpHeaders headers = downstream.getRequest().getHeaders();

            assertThat(headers.getFirst("X-User-Id")).isEqualTo("1");
            assertThat(headers.getFirst("X-User-Email")).isEqualTo("real@test.com");
            assertThat(headers.getFirst("X-User-Roles")).isEqualTo("ROLE_USER");
            assertThat(headers.getFirst("X-User-Anonymous")).isEqualTo("false");
        }
    }

    @Nested
    @DisplayName("Error response body")
    class ErrorResponse {

        @Test
        @DisplayName("401 содержит JSON body с ошибкой")
        void errorResponseHasJsonBody() {
            MockServerWebExchange exchange = exchangeWithPath("/api/v1/users/me");

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(exchange.getResponse().getHeaders().getContentType())
                    .isEqualTo(MediaType.APPLICATION_JSON);
        }
    }
}