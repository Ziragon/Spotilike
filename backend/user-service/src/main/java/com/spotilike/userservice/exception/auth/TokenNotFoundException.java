package com.spotilike.userservice.exception.auth;

import com.spotilike.shared.exception.base.BaseException;
import com.spotilike.shared.exception.base.ErrorType;

public class TokenNotFoundException extends BaseException {

    public TokenNotFoundException() {
        super("Refresh token not found", ErrorType.TOKEN_NOT_FOUND);
    }
}