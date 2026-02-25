package com.spotilike.userservice.exception.base;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorType {

    // 400
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR"),

    // 401
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED"),
    TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "TOKEN_REVOKED"),
    TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "TOKEN_NOT_FOUND"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS"),

    // 403
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "ACCESS_DENIED"),

    // 404
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND"),

    // 409
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "DUPLICATE_EMAIL"),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE"),

    // 500
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");

    private final HttpStatus status;
    private final String code;
}