package com.spotilike.userservice.service;

import com.spotilike.userservice.dto.response.AuthResponse;
import com.spotilike.userservice.exception.auth.InvalidCredentialsException;
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

        return new AuthResponse(accessToken, refreshToken);
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

        return new AuthResponse(accessToken, refreshToken);
    }
}
