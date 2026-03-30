package com.spotilike.userservice.exception.auth;

import com.spotilike.userservice.exception.base.BaseException;
import com.spotilike.userservice.exception.base.ErrorType;

public class TokenNotFoundException extends BaseException {

    public TokenNotFoundException() {
        super("Refresh token not found", ErrorType.TOKEN_NOT_FOUND);
    }

    public TokenNotFoundException(String tokenType) {
        super(
                tokenType + " token not found",
                ErrorType.TOKEN_NOT_FOUND
        );
    }
}