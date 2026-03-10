package com.spotilike.gatewayservice.filter;

import com.spotilike.gatewayservice.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
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
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Value("${gateway.open-paths}")
    private List<String> openPaths;

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
                })
                .build();
        exchange = exchange.mutate().request(request).build();

        if (isOpenPath(path)) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            return onError(exchange, "Missing JWT Token");
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.isValid(token)) {
            log.warn("Invalid JWT for path: {}", path);
            return onError(exchange, "Token is not valid");
        }

        Claims claims = jwtUtil.getAllClaimsFromToken(token);

        Integer userIdClaim = claims.get("userId", Integer.class);
        String email = claims.getSubject();
        if (userIdClaim == null || email == null) {
            return onError(exchange, "Token does not have claims");
        }

        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        String rolesHeader = (roles != null) ? String.join(",", roles) : "";

        ServerHttpRequest mutatedRequest = request.mutate()
                .headers(h -> h.remove(HttpHeaders.AUTHORIZATION))
                .header("X-User-Id", String.valueOf(userIdClaim))
                .header("X-User-Email", email)
                .header("X-User-Roles", rolesHeader)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private boolean isOpenPath(String path) {
        return openPaths.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @SuppressWarnings("NullableProblems")
    private Mono<Void> onError(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = ("{\"error\":\"" + message + "\"}").getBytes();
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}