package com.spotilike.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
public class GatewaySecretFilter extends OncePerRequestFilter {

    @Value("${application.security.gateway.header-name}")
    private String gatewayHeaderName;

    @Value("${application.security.gateway.header-key}")
    private String expectedSecret;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        log.debug("GatewaySecretFilter called for {}",
                request.getRequestURI());

        String secretHeader = request.getHeader(gatewayHeaderName);

        if (secretHeader == null || !MessageDigest.isEqual(secretHeader.getBytes(StandardCharsets.UTF_8), expectedSecret.getBytes(StandardCharsets.UTF_8))) {
            log.error("Security violation: Request without valid Gateway Secret key from IP: {}", request.getRemoteAddr());

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("""
                {
                    "error": "UNAUTHORIZED",
                    "message": "Direct access is prohibited"
                }
                """);
            return;
        }

        filterChain.doFilter(request,response);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();

        return path.startsWith("/api-docs")
                || path.startsWith("/actuator/health");
    }
}
