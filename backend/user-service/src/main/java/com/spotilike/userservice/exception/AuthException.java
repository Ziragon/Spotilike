package com.spotilike.userservice.exception;

import java.util.Map;

public class AuthException extends BaseException {

    public AuthException(String message, ErrorType errorType) {
        super(message, errorType);
    }

    public AuthException(String message, ErrorType errorType, Map<String, Object> details) {
        super(message, errorType, details);
    }
}