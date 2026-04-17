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
import java.time.OffsetDateTime;
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

    @Value("${application.security.jwt.refresh-token.grace-period-seconds:30}")
    private long gracePeriodSeconds;

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
                .familyId(UUID.randomUUID())
                .expiresAt(OffsetDateTime.now(clock)
                        .plus(refreshExpiration, ChronoUnit.MILLIS))
                .build();

        refreshTokenRepository.save(refreshToken);
        return clearToken;
    }

    @Transactional
    public String rotateRefreshToken(String clearToken, String ipAddress, String deviceInfo) {
        RefreshToken oldToken = lookupByToken(clearToken)
                .orElseThrow(TokenNotFoundException::new);

        UUID familyId = oldToken.getFamilyId();

        if (oldToken.getRevokedAt() != null) {
            log.warn("SECURITY: Attempt to use revoked token. Revoking family {}", familyId);
            int revoked = refreshTokenRepository.revokeByFamilyId(familyId);
            log.warn("SECURITY: Revoked {} tokens in family {}", revoked, familyId);
            throw new TokenRevokedException();
        }

        if (oldToken.getExpiresAt().isBefore(OffsetDateTime.now(clock))) {
            throw new TokenExpiredException("refresh");
        }

        // Reuse detection / Grace period
        if (oldToken.getConsumedAt() != null) {
            long secondsSinceConsumed = ChronoUnit.SECONDS.between(
                    oldToken.getConsumedAt(), OffsetDateTime.now(clock));

            if (secondsSinceConsumed <= gracePeriodSeconds) {
                // Grace period активен
                log.info("Grace period active for token family {}", familyId);
            } else {
                log.warn("SECURITY: Token reuse detected! Revoking family {}", familyId);
                int revoked = refreshTokenRepository.revokeByFamilyId(familyId);
                log.warn("SECURITY: Revoked {} tokens in family {}", revoked, familyId);
                throw new TokenRevokedException();
            }
        } else {
            // Токен использован
            oldToken.setConsumedAt(OffsetDateTime.now(clock));
            refreshTokenRepository.save(oldToken);
        }

        String newClearToken = UUID.randomUUID().toString();

        RefreshToken newToken = RefreshToken.builder()
                .user(oldToken.getUser())
                .familyId(familyId)
                .tokenHash(TokenHashUtil.hash(newClearToken))
                .ipAddress(ipAddress)
                .deviceInfo(deviceInfo)
                .expiresAt(OffsetDateTime.now(clock).plus(refreshExpiration, ChronoUnit.MILLIS))
                .build();

        refreshTokenRepository.save(newToken);
        return newClearToken;
    }

    @Transactional
    public RefreshToken validateRefreshToken(String clearToken) {
        RefreshToken token = lookupByToken(clearToken)
                .orElseThrow(() -> {
                    log.warn("Refresh token not found");
                    return new TokenNotFoundException();
                });

        Long userId = token.getUser().getId();

        if (token.getRevokedAt() != null) {
            log.warn("SECURITY: Revoked token reuse for user {}", userId);
            revokeAllTokens(userId);
            throw new TokenRevokedException();
        }

        if (token.getExpiresAt().isBefore(OffsetDateTime.now(clock))) {
            log.info("Expired refresh token for user {}", userId);
            token.setRevokedAt(OffsetDateTime.now(clock));
            refreshTokenRepository.save(token);
            throw new TokenExpiredException("refresh");
        }

        return token;
    }

    @Transactional
    public void revokeToken(String clearToken) {
        lookupByToken(clearToken).ifPresentOrElse(
                token -> {
                    token.setRevokedAt(OffsetDateTime.now(clock));
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

    private Optional<RefreshToken> lookupByToken(String clearToken) {
        return refreshTokenRepository
                .findByTokenHash(TokenHashUtil.hash(clearToken));
    }

    private void revokeAllTokens(Long userId) {
        int count = refreshTokenRepository.revokeAllByUserId(userId);
        log.info("Revoked {} token(s) for user {}", count, userId);
    }
}