package com.spotilike.gatewayservice.filter;

import com.spotilike.gatewayservice.config.AppGatewayProperties;
import com.spotilike.gatewayservice.util.GatewayErrorResponse;
import com.spotilike.gatewayservice.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;
    private final AppGatewayProperties gatewayProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    @SuppressWarnings("NullableProblems")
    public @NonNull Mono<Void> filter(@NonNull ServerWebExchange exchange,
                                      @NonNull GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        request = request.mutate()
                .headers(h -> {
                    h.remove("X-User-Id");
                    h.remove("X-User-Email");
                    h.remove("X-User-Roles");
                    h.remove("X-User-Anonymous");
                })
                .build();
        exchange = exchange.mutate().request(request).build();

        boolean open = isOpenPath(path);

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        boolean hasToken = authHeader != null && authHeader.startsWith("Bearer ");

        // Нет токена
        if (!hasToken) {
            if (open) {
                return chain.filter(withAnonymous(exchange, request));
            }
            log.warn("Missing Authorization header for path: {}", path);
            return GatewayErrorResponse.send(exchange, HttpStatus.UNAUTHORIZED, "Missing JWT Token");
        }

        String token = authHeader.substring(7);

        // Токен невалидный
        if (!jwtUtil.isValid(token)) {
            if (open) {
                return chain.filter(withAnonymous(exchange, request));
            }
            log.warn("Invalid JWT for path: {}", path);
            return GatewayErrorResponse.send(exchange, HttpStatus.UNAUTHORIZED, "Token is not valid");
        }

        // Токен валидный - парсим
        Claims claims = jwtUtil.getAllClaimsFromToken(token);

        Integer userIdClaim = claims.get("userId", Integer.class);
        String email = claims.getSubject();

        if (userIdClaim == null || email == null) {
            if (open) {
                return chain.filter(withAnonymous(exchange, request));
            }
            log.warn("JWT missing required claims for path: {}", path);
            return GatewayErrorResponse.send(exchange, HttpStatus.UNAUTHORIZED, "Token does not have claims");
        }

        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        String rolesHeader = (roles != null) ? String.join(",", roles) : "";

        ServerHttpRequest mutatedRequest = request.mutate()
                .headers(h -> h.remove(HttpHeaders.AUTHORIZATION))
                .header("X-User-Id", String.valueOf(userIdClaim))
                .header("X-User-Email", email)
                .header("X-User-Roles", rolesHeader)
                .header("X-User-Anonymous", "false")
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private ServerWebExchange withAnonymous(ServerWebExchange exchange, ServerHttpRequest request) {
        ServerHttpRequest anonymousRequest = request.mutate()
                .headers(h -> h.remove(HttpHeaders.AUTHORIZATION))
                .header("X-User-Anonymous", "true")
                .build();
        return exchange.mutate().request(anonymousRequest).build();
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private boolean isOpenPath(String path) {
        return gatewayProperties.getOpenPaths().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }
}