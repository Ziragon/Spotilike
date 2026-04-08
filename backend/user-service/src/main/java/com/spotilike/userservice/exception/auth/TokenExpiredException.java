package com.spotilike.userservice.exception.auth;

import com.spotilike.shared.exception.base.BaseException;
import com.spotilike.shared.exception.base.ErrorType;

import java.util.Map;

public class TokenExpiredException extends BaseException {

    // Передается тип токена (access, refresh)
    public TokenExpiredException(String tokenType) {
        super(
                tokenType + " token has expired",
                ErrorType.TOKEN_EXPIRED,
                Map.of("tokenType", tokenType)
        );
    }
}