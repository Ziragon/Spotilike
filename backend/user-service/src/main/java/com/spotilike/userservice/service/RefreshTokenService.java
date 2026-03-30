package com.spotilike.userservice.service;

import com.spotilike.userservice.exception.auth.TokenRevokedException;
import com.spotilike.userservice.exception.auth.TokenNotFoundException;
import com.spotilike.userservice.model.RefreshToken;
import com.spotilike.userservice.model.User;
import com.spotilike.userservice.exception.auth.TokenExpiredException;
import com.spotilike.userservice.exception.resource.UserNotFoundException;
import com.spotilike.userservice.repository.RefreshTokenRepository;
import com.spotilike.userservice.repository.UserRepository;
import com.spotilike.userservice.util.TokenHashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Value("${application.security.jwt.refresh-token.expiration}")
    private long refreshExpiration;

    private Optional<RefreshToken> lookupByToken(String clearToken) {
        return refreshTokenRepository
                .findByTokenHash(TokenHashUtil.hash(clearToken));
    }

    private void revokeAllTokens(Long userId) {
        int count = refreshTokenRepository.revokeAllByUserId(userId);
        log.info("Revoked {} token(s) for user {}", count, userId);
    }

    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByToken(String clearToken) {
        return lookupByToken(clearToken);
    }

    @Transactional
    public String createRefreshToken(Long userId, String ipAddress,
                                     String deviceInfo) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        int revoked = refreshTokenRepository
                .revokeByUserIdAndDeviceInfo(userId, deviceInfo);

        if (revoked > 0) {
            log.info("Revoked {} old token(s) for user {} on device {}",
                    revoked, userId, deviceInfo);
        }

        String clearToken = UUID.randomUUID().toString();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(TokenHashUtil.hash(clearToken))
                .ipAddress(ipAddress)
                .deviceInfo(deviceInfo)
                .expiresAt(LocalDateTime.now(clock)
                        .plus(refreshExpiration, ChronoUnit.MILLIS))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);
        return clearToken;
    }

    @Transactional
    public RefreshToken validateRefreshToken(String clearToken) {
        RefreshToken token = lookupByToken(clearToken)
                .orElseThrow(() -> {
                    log.warn("Refresh token not found");
                    return new TokenNotFoundException();
                });

        Long userId = token.getUser().getId();

        if (token.isRevoked()) {
            log.warn("SECURITY: Revoked token reuse for user {}", userId);
            revokeAllTokens(userId);
            throw new TokenRevokedException();
        }

        if (token.getExpiresAt().isBefore(LocalDateTime.now(clock))) {
            log.info("Expired refresh token for user {}", userId);
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            throw new TokenExpiredException("refresh");
        }

        return token;
    }

    @Transactional
    public void revokeToken(String clearToken) {
        lookupByToken(clearToken).ifPresentOrElse(
                token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                    log.info("Token revoked for user {}",
                            token.getUser().getId());
                },
                () -> log.debug("Token to revoke not found")
        );
    }

    @Transactional
    public void revokeAllUserTokens(Long userId) {
        revokeAllTokens(userId);
    }
}