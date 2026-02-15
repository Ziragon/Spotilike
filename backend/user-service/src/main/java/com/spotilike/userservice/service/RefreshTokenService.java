package com.spotilike.userservice.service;

import com.spotilike.userservice.dto.model.RefreshToken;
import com.spotilike.userservice.dto.model.User;
import com.spotilike.userservice.exception.UserNotFoundException;
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

    public String createRefreshToken(Long userId, String ipAddress, String deviceInfo) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User with id " + userId + " not found"));

        String clearToken = UUID.randomUUID().toString();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hashToken(clearToken))
                .ipAddress(ipAddress)
                .deviceInfo(deviceInfo)
                .expiresAt(LocalDateTime.now().plus(refreshExpiration, ChronoUnit.MILLIS))
                .isRevoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);

        return clearToken;
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(token);
            throw new RuntimeException("Refresh token was expired. Please make a new signin request");
        }
        return token;
    }

    @Transactional
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
