package com.spotilike.userservice.exception.base;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorType {

    // 400
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ErrorCategory.VALIDATION),

    // 401
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", ErrorCategory.AUTH),
    TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "TOKEN_REVOKED", ErrorCategory.AUTH),
    TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "TOKEN_NOT_FOUND", ErrorCategory.AUTH),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", ErrorCategory.AUTH),

    // 403
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "ACCESS_DENIED", ErrorCategory.AUTH),

    // 404
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", ErrorCategory.RESOURCE),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ErrorCategory.RESOURCE),
    ROLE_NOT_FOUND(HttpStatus.NOT_FOUND, "ROLE_NOT_FOUND", ErrorCategory.RESOURCE),

    // 409
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", ErrorCategory.RESOURCE),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", ErrorCategory.RESOURCE),

    // 500
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", ErrorCategory.SYSTEM),

    // 503
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", ErrorCategory.SYSTEM);

    private final HttpStatus status;
    private final String code;
    private final ErrorCategory category;

    public enum ErrorCategory {
        AUTH, VALIDATION, RESOURCE, SYSTEM
    }
}