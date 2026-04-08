package com.spotilike.userservice.controller;

import com.spotilike.userservice.dto.request.LoginRequest;
import com.spotilike.userservice.dto.request.RegisterRequest;
import com.spotilike.userservice.dto.response.AuthResponse;
import com.spotilike.shared.security.UserPrincipal;
import com.spotilike.userservice.service.AuthService;
import com.spotilike.userservice.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
                RequestUtil.extractClientIp(httpReq),
                RequestUtil.extractDeviceInfo(httpReq)
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
                RequestUtil.extractClientIp(httpReq),
                RequestUtil.extractDeviceInfo(httpReq)
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/test")
    public ResponseEntity<String> hello(@AuthenticationPrincipal UserPrincipal principal) {

        if (principal == null) {
            return ResponseEntity.ok("Hello, you're guest");
        }

        return ResponseEntity.ok("Hello, you're auth user: " + principal.email());
    }
}
