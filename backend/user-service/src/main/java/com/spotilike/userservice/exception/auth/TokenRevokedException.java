package com.spotilike.userservice.exception.auth;

import com.spotilike.userservice.exception.base.BaseException;
import com.spotilike.userservice.exception.base.ErrorType;

import java.util.Map;

public class TokenRevokedException extends BaseException {

    public TokenRevokedException() {
        super(
                "Refresh token has been revoked",
                ErrorType.TOKEN_REVOKED
        );
    }

    public TokenRevokedException(Long userId) {
        super(
                "Refresh token has been revoked",
                ErrorType.TOKEN_REVOKED,
                Map.of("userId", userId)
        );
    }
}