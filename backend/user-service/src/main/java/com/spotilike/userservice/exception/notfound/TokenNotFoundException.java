package com.spotilike.userservice.exception.notfound;

import com.spotilike.userservice.exception.base.ErrorType;

public class TokenNotFoundException extends NotFoundException {
    public TokenNotFoundException(Object identifier) {
        super("RefreshToken", identifier, ErrorType.TOKEN_NOT_FOUND);
    }
}
