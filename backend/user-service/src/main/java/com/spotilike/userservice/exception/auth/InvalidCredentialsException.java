package com.spotilike.userservice.exception.auth;

import com.spotilike.userservice.exception.base.BaseException;
import com.spotilike.userservice.exception.base.ErrorType;

import java.util.Map;

public class InvalidCredentialsException extends BaseException {

    public InvalidCredentialsException() {
        super("Invalid email or password",
                ErrorType.INVALID_CREDENTIALS);
    }

    public InvalidCredentialsException(String context) {
        super(
                "Invalid credentials: " + context,
                ErrorType.INVALID_CREDENTIALS,
                Map.of("context", context)
        );
    }
}