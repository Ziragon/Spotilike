package com.spotilike.userservice.exception;

import org.springframework.http.HttpStatus;

public class BaseException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus status;

    protected BaseException(String message, String errorCode, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }
}
