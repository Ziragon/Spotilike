package com.spotilike.gatewayservice.config;

import com.spotilike.gatewayservice.util.GatewayErrorResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;

@Slf4j
@Component
@Order(-2)
public class GatewayErrorHandler implements ErrorWebExceptionHandler {

    @Override
    @SuppressWarnings("NullableProblems")
    public @NonNull Mono<Void> handle(@NonNull ServerWebExchange exchange,
                                      @NonNull Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        if (isConnectionError(ex)) {
            log.error("Downstream service unreachable: {}", exchange.getRequest().getURI());
            return GatewayErrorResponse.send(exchange, HttpStatus.BAD_GATEWAY, "Service unavailable");
        }

        log.error("Unexpected gateway error at {}: {}", exchange.getRequest().getURI(), ex.getMessage(), ex);
        return GatewayErrorResponse.send(exchange, HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    private boolean isConnectionError(Throwable ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof ConnectException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}