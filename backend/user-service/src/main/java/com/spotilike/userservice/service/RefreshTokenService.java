package com.spotilike.userservice.service;

import com.spotilike.userservice.exception.notfound.TokenNotFoundException;
import com.spotilike.userservice.model.RefreshToken;
import com.spotilike.userservice.model.User;
import com.spotilike.userservice.exception.auth.TokenExpiredException;
import com.spotilike.userservice.exception.notfound.UserNotFoundException;
import com.spotilike.userservice.repository.RefreshTokenRepository;
import com.spotilike.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Value("${application.security.jwt.refresh-token.expiration}")
    private long refreshExpiration;

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing token", e);
        }
    }

    @Transactional
    public String createRefreshToken(Long userId, String ipAddress, String deviceInfo) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        String clearToken = UUID.randomUUID().toString();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hashToken(clearToken))
                .ipAddress(ipAddress)
                .deviceInfo(deviceInfo)
                .expiresAt(LocalDateTime.now().plus(refreshExpiration, ChronoUnit.MILLIS))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);

        return clearToken;
    }

    @Transactional
    public RefreshToken validateRefreshToken(String clearToken) {
        RefreshToken token = findByToken(clearToken)
                .orElseThrow(() -> new TokenNotFoundException(clearToken));

        if (token.isRevoked()) {
            revokeAllUserTokens(token.getUser().getId());
            throw new TokenRevokedException("Refresh token has been revoked");
        }

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            throw new TokenExpiredException(clearToken);
        }

        return token;
    }

    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByToken(String clearToken) {
        String hash = hashToken(clearToken);
        return refreshTokenRepository.findByTokenHash(hash);
    }

    @Transactional
    public void revokeToken(String clearToken) {
        findByToken(clearToken).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }
}
