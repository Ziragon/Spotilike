package com.spotilike.userservice.exception.auth;

import com.spotilike.userservice.exception.base.ErrorType;

import java.util.Map;

public class InvalidCredentialsException extends AuthException {

    public InvalidCredentialsException() {
        super("Invalid email or password", ErrorType.INVALID_CREDENTIALS);
    }

    public InvalidCredentialsException(String field) {
        super(
                "Invalid credentials",
                ErrorType.INVALID_CREDENTIALS,
                Map.of("hint", field)
        );
    }
}