package com.spotilike.userservice.service;

import com.spotilike.userservice.exception.auth.TokenRevokedException;
import com.spotilike.userservice.exception.notfound.TokenNotFoundException;
import com.spotilike.userservice.model.RefreshToken;
import com.spotilike.userservice.model.User;
import com.spotilike.userservice.exception.auth.TokenExpiredException;
import com.spotilike.userservice.exception.notfound.UserNotFoundException;
import com.spotilike.userservice.repository.RefreshTokenRepository;
import com.spotilike.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
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
            throw new AssertionError(
                    "SHA-256 algorithm must be available in every JVM", e
            );
        }
    }

    @Transactional
    public String createRefreshToken(Long userId, String ipAddress,
                                     String deviceInfo) {
        log.debug("Creating refresh token for user {} from IP {} device {}",
                userId, ipAddress, deviceInfo);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("Attempt to create token for non-existent user {}",
                            userId);
                    return new UserNotFoundException(userId);
                });

        int revoked = refreshTokenRepository
                .revokeByUserIdAndDeviceInfo(userId, deviceInfo);

        if (revoked > 0) {
            log.info("Revoked {} old token(s) for user {} on device {}",
                    revoked, userId, deviceInfo);
        }

        String clearToken = UUID.randomUUID().toString();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hashToken(clearToken))
                .ipAddress(ipAddress)
                .deviceInfo(deviceInfo)
                .expiresAt(LocalDateTime.now()
                        .plus(refreshExpiration, ChronoUnit.MILLIS))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);

        log.info("Refresh token created for user {} from IP {}",
                userId, ipAddress);

        return clearToken;
    }

    @Transactional
    public RefreshToken validateRefreshToken(String clearToken) {
        log.debug("Validating refresh token");

        RefreshToken token = findByToken(clearToken)
                .orElseThrow(() -> {
                    log.warn("Refresh token not found — "
                            + "possible invalid or already deleted token");
                    return new TokenNotFoundException();
                });

        Long userId = token.getUser().getId();

        if (token.isRevoked()) {
            log.warn("SECURITY: Attempt to use revoked token for user {}. "
                            + "Revoking ALL sessions. IP: {}, device: {}",
                    userId, token.getIpAddress(), token.getDeviceInfo());

            revokeAllUserTokens(userId);
            throw new TokenRevokedException();
        }

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.info("Expired refresh token used by user {}", userId);

            token.setRevoked(true);
            refreshTokenRepository.save(token);
            throw new TokenExpiredException("refresh");
        }

        log.debug("Refresh token valid for user {}", userId);
        return token;
    }

    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByToken(String clearToken) {
        String hash = hashToken(clearToken);
        return refreshTokenRepository.findByTokenHash(hash);
    }

    @Transactional
    public void revokeToken(String clearToken) {
        findByToken(clearToken).ifPresentOrElse(
                token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                    log.info("Token revoked for user {}",
                            token.getUser().getId());
                },
                () -> log.debug("Token to revoke not found - "
                        + "already deleted or invalid")
        );
    }

    @Transactional
    public void revokeAllUserTokens(Long userId) {
        int count = refreshTokenRepository.revokeAllByUserId(userId);
        log.info("Revoked {} token(s) for user {}", count, userId);
    }
}