package com.spotilike.userservice.exception;

public class TokenExpiredException extends AuthException {
    public TokenExpiredException(String tokenType) {
        super(tokenType + " token has expired", "TOKEN_EXPIRED_" + tokenType.toUpperCase());
    }
}