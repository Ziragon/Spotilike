package com.spotilike.userservice.exception;

import java.util.Map;

public class TokenExpiredException extends AuthException {

    public TokenExpiredException(String tokenType) {
        super(
                tokenType + " token has expired",
                ErrorType.TOKEN_EXPIRED,
                Map.of("tokenType", tokenType)
        );
    }
}