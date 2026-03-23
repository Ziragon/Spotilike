package com.spotilike.userservice.controller;

import com.spotilike.userservice.dto.request.LoginRequest;
import com.spotilike.userservice.dto.request.RegisterRequest;
import com.spotilike.userservice.dto.response.AuthResponse;
import com.spotilike.userservice.security.UserPrincipal;
import com.spotilike.userservice.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest req,
            HttpServletRequest httpReq
    ) {
        log.info("Registration attempt: email={}", req.email());

        AuthResponse response = authService.register(
                req.email(),
                req.password(),
                req.username(),
                extractIp(httpReq),
                extractDevice(httpReq)
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest req,
            HttpServletRequest httpReq
    ) {
        log.info("Login attempt: email={}", req.email());

        AuthResponse response = authService.login(
                req.email(),
                req.password(),
                extractIp(httpReq),
                extractDevice(httpReq)
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/test")
    public ResponseEntity<String> hello(@AuthenticationPrincipal UserPrincipal principal) {

        if (principal == null) {
            return ResponseEntity.ok("Hello, you're guest");
        }

        return ResponseEntity.ok("Hello, you're auth user: " + principal.email());
    }

    private String extractIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    private String extractDevice(HttpServletRequest req) {
        String ua = req.getHeader("User-Agent");
        return (ua != null && !ua.isBlank()) ? ua : "unknown";
    }
}
