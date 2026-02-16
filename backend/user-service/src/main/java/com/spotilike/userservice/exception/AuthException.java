package com.spotilike.userservice.exception;

import org.springframework.http.HttpStatus;

public class AuthException extends BaseException {
    public AuthException(String message, String errorCode) {
        super(message, errorCode, HttpStatus.UNAUTHORIZED);
    }
}