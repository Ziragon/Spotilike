package com.spotilike.gatewayservice.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    // Тестовый ключ — 32 байта в Base64
    private static final String SECRET_KEY = "dGhpcy1pcy1hLXNlY3JldC1rZXktMzItYnl0ZXMhZXC";
    private static final String DIFFERENT_SECRET = "YW5vdGhlci1kaWZmZXJlbnQtc2VjcmV0LWtleSEhZXC";

    private JwtUtil jwtUtil;
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET_KEY);
        signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET_KEY));
    }

    private String buildToken(String subject, Map<String, Object> claims, Date expiration) {
        return Jwts.builder()
                .subject(subject)
                .claims(claims)
                .issuedAt(new Date())
                .expiration(expiration)
                .signWith(signingKey)
                .compact();
    }

    private String buildToken(String subject, Map<String, Object> claims) {
        return buildToken(subject, claims, new Date(System.currentTimeMillis() + (long) 3600000));
    }

    private String buildValidToken() {
        return buildToken(
                "user@test.com",
                Map.of("userId", 1, "roles", List.of("ROLE_USER"))
                // 1 час
        );
    }

    private String buildExpiredToken() {
        return buildToken(
                "user@test.com",
                Map.of("userId", 1, "roles", List.of("ROLE_USER")),
                new Date(System.currentTimeMillis() - 1000) // 1 секунда назад
        );
    }

    private String buildTokenWithDifferentKey() {
        SecretKey differentKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(DIFFERENT_SECRET));
        return Jwts.builder()
                .subject("user@test.com")
                .claims(Map.of("userId", 1, "roles", List.of("ROLE_USER")))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(differentKey)
                .compact();
    }

    // -- Тесты --

    @Nested
    @DisplayName("isValid()")
    class IsValid {

        @Test
        @DisplayName("Валидный токен (true))")
        void validToken_returnsTrue() {
            String token = buildValidToken();

            assertThat(jwtUtil.isValid(token)).isTrue();
        }

        @Test
        @DisplayName("Просроченный токен (false))")
        void expiredToken_returnsFalse() {
            String token = buildExpiredToken();

            assertThat(jwtUtil.isValid(token)).isFalse();
        }

        @Test
        @DisplayName("Токен подписан другим ключом (false)")
        void wrongSignature_returnsFalse() {
            String token = buildTokenWithDifferentKey();

            assertThat(jwtUtil.isValid(token)).isFalse();
        }

        @Test
        @DisplayName("Мусорная строка (false)")
        void garbageString_returnsFalse() {
            assertThat(jwtUtil.isValid("not.a.token")).isFalse();
        }

        @Test
        @DisplayName("Пустая строка (false)")
        void emptyString_returnsFalse() {
            assertThat(jwtUtil.isValid("")).isFalse();
        }

        @Test
        @DisplayName("Повреждённый токен (false)")
        void tamperedToken_returnsFalse() {
            String token = buildValidToken();
            String tampered = token.substring(0, token.length() - 1) + "Z";

            assertThat(jwtUtil.isValid(tampered)).isFalse();
        }
    }

    @Nested
    @DisplayName("isTokenExpired()")
    class IsTokenExpired {

        @Test
        @DisplayName("Не просроченный (false)")
        void validToken_returnsFalse() {
            String token = buildValidToken();

            assertThat(jwtUtil.isTokenExpired(token)).isFalse();
        }

        @Test
        @DisplayName("Просроченный (true)")
        void expiredToken_returnsTrue() {
            String token = buildExpiredToken();

            assertThat(jwtUtil.isTokenExpired(token)).isTrue();
        }

        @Test
        @DisplayName("Невалидный токен (true)")
        void invalidToken_returnsTrue() {
            assertThat(jwtUtil.isTokenExpired("garbage")).isTrue();
        }
    }

    @Nested
    @DisplayName("getAllClaimsFromToken()")
    class GetAllClaims {

        @Test
        @DisplayName("Парсит subject (email)")
        void parsesSubject() {
            String token = buildValidToken();

            Claims claims = jwtUtil.getAllClaimsFromToken(token);

            assertThat(claims.getSubject()).isEqualTo("user@test.com");
        }

        @Test
        @DisplayName("Парсит userId")
        void parsesUserId() {
            String token = buildValidToken();

            Claims claims = jwtUtil.getAllClaimsFromToken(token);

            assertThat(claims.get("userId", Integer.class)).isEqualTo(1);
        }

        @Test
        @DisplayName("Парсит roles")
        @SuppressWarnings("unchecked")
        void parsesRoles() {
            String token = buildToken(
                    "admin@test.com",
                    Map.of("userId", 2, "roles", List.of("ROLE_ADMIN", "ROLE_USER"))
            );

            Claims claims = jwtUtil.getAllClaimsFromToken(token);
            List<String> roles = claims.get("roles", List.class);

            assertThat(roles).containsExactly("ROLE_ADMIN", "ROLE_USER");
        }

        @Test
        @DisplayName("Невалидный токен (exception)")
        void invalidToken_throwsException() {
            assertThatThrownBy(() -> jwtUtil.getAllClaimsFromToken("garbage"))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Токен с другим ключом (exception)")
        void wrongKey_throwsException() {
            String token = buildTokenWithDifferentKey();

            assertThatThrownBy(() -> jwtUtil.getAllClaimsFromToken(token))
                    .isInstanceOf(Exception.class);
        }
    }
}