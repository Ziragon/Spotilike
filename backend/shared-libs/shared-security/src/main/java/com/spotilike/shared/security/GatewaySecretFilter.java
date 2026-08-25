package com.spotilike.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
public class GatewaySecretFilter extends AbstractSecurityClass {

    private final String gatewayHeaderName;
    private final String expectedSecret;

    public GatewaySecretFilter(
            @Value("${application.security.gateway.header-name}") String gatewayHeaderName,
            @Value("${application.security.gateway.header-key}") String expectedSecret) {
        this.gatewayHeaderName = gatewayHeaderName;
        this.expectedSecret = expectedSecret;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        log.debug("GatewaySecretFilter called for {}",
                request.getRequestURI());

        String secretHeader = request.getHeader(gatewayHeaderName);

        if (secretHeader == null || !MessageDigest.isEqual(secretHeader.getBytes(StandardCharsets.UTF_8), expectedSecret.getBytes(StandardCharsets.UTF_8))) {
            log.error("Security violation: Request without valid Gateway Secret key from IP: {}", request.getRemoteAddr());

            this.writeUnauthorized(response, "Direct access is prohibited");
            return;
        }

        filterChain.doFilter(request,response);
    }
}
