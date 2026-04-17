package com.spotilike.userservice.service;

import com.spotilike.userservice.dto.response.AuthResponse;
import com.spotilike.userservice.dto.response.UserDto;
import com.spotilike.userservice.exception.auth.InvalidCredentialsException;
import com.spotilike.userservice.exception.auth.TokenNotFoundException;
import com.spotilike.userservice.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse register(String email,
                                 String rawPassword,
                                 String username,
                                 String ip,
                                 String device) {

        User user = userService.createUser(email, rawPassword, username);

        String accessToken  = jwtService.generateToken(user);
        String refreshToken = refreshTokenService
                .createRefreshToken(user.getId(), ip, device);

        return new AuthResponse(
                accessToken,
                refreshToken,
                jwtService.getExpirationTime(),
                UserDto.from(user)
        );
    }

    @Transactional
    public AuthResponse login(String email,
                              String rawPassword,
                              String ip,
                              String device) {

        User user = userService.findByEmail(email);

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String accessToken  = jwtService.generateToken(user);
        String refreshToken = refreshTokenService
                .createRefreshToken(user.getId(), ip, device);

        return new AuthResponse(
                accessToken,
                refreshToken,
                jwtService.getExpirationTime(),
                UserDto.from(user)
        );
    }

    @Transactional
    public AuthResponse refreshToken(String oldRefreshToken, String ip, String device) {

        String newRefreshToken = refreshTokenService
                .rotateRefreshToken(oldRefreshToken, ip, device);

        User user = refreshTokenService.findByToken(oldRefreshToken)
                .orElseThrow(TokenNotFoundException::new)
                .getUser();

        String newAccessToken = jwtService.generateToken(user);

        return new AuthResponse(
                newAccessToken,
                newRefreshToken,
                jwtService.getExpirationTime(),
                UserDto.from(user)
        );
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revokeToken(refreshToken);
    }

    @Transactional
    public void logoutAll(Long userId) {
        refreshTokenService.revokeAllUserTokens(userId);
    }

    @Transactional
    public void logoutOthers(Long userId, String refreshToken) {
        refreshTokenService.revokeAllOthers(userId, refreshToken);
    }
}
