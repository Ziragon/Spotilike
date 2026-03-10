package com.spotilike.gatewayservice;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayIntegrationTest {

    @Autowired
    private WebTestClient webClient;

    @Value("${application.security.jwt.secret-key}")
    private String secretKeyBase64;

    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKeyBase64));
    }

    private String validToken() {
        return Jwts.builder()
                .subject("user@test.com")
                .claims(Map.of("userId", 1, "roles", List.of("ROLE_USER")))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(signingKey)
                .compact();
    }

    @Test
    @DisplayName("Открытый путь без токена")
    void openPath_passesWithoutToken() {
        webClient.get()
                .uri("/api/v1/auth/login")
                .exchange()
                .expectStatus().isEqualTo(502);
    }

    @Test
    @DisplayName("Защищённый путь без токена (401)")
    void protectedPath_noToken_returns401() {
        webClient.get()
                .uri("/api/v1/users/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Защищённый путь с валидным токеном (пропускает)")
    void protectedPath_validToken_passes() {
        webClient.get()
                .uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                .exchange()
                .expectStatus().isEqualTo(502);
    }
}