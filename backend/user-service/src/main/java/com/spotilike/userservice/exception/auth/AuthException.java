package com.spotilike.userservice.exception.auth;

import com.spotilike.userservice.exception.base.BaseException;
import com.spotilike.userservice.exception.base.ErrorType;

import java.util.Map;

public class AuthException extends BaseException {

    public AuthException(String message, ErrorType errorType) {
        super(message, errorType);
    }

    public AuthException(String message, ErrorType errorType,
                         Map<String, Object> details) {
        super(message, errorType, details);
    }

    public AuthException(String message, ErrorType errorType,
                         Throwable cause) {
        super(message, errorType, cause);
    }
}