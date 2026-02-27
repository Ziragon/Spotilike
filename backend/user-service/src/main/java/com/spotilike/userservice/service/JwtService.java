package com.spotilike.userservice.service;

import com.spotilike.userservice.model.Role;
import com.spotilike.userservice.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class JwtService {

    private final SecretKey signingKey;
    private final long jwtExpiration;

    public JwtService(
            @Value("${application.security.jwt.secret-key}") String secretKey,
            @Value("${application.security.jwt.expiration}") long jwtExpiration
    ) {
        this.signingKey = Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(secretKey)
        );
        this.jwtExpiration = jwtExpiration;
        log.info("JwtService initialized, token TTL: {} ms", jwtExpiration);
    }

    public String generateToken(User user) {
        Map<String, Object> extraClaims = Map.of(
                "userId", user.getId(),
                "roles", user.getRoles().stream()
                        .map(Role::getName)
                        .toList()
        );

        String token = buildToken(extraClaims, user.getEmail());

        log.debug("JWT generated for user {} with roles {}",
                user.getId(), extraClaims.get("roles"));

        return token;
    }

    private String buildToken(Map<String, Object> extraClaims,
                              String subject) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(
                        System.currentTimeMillis() + jwtExpiration))
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token,
                              Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            boolean valid = username.equals(userDetails.getUsername())
                    && !isTokenExpired(token);

            if (!valid) {
                log.warn("JWT validation failed for user {}",
                        userDetails.getUsername());
            }

            return valid;
        } catch (JwtException e) {
            log.warn("Malformed/tampered JWT: {}", e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            log.warn("Empty or null JWT token");
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration)
                .before(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}