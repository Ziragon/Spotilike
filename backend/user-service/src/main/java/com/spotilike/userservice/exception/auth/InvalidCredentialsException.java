package com.spotilike.userservice.exception.auth;

import com.spotilike.shared.exception.base.BaseException;
import com.spotilike.shared.exception.base.ErrorType;

public class InvalidCredentialsException extends BaseException {

    public InvalidCredentialsException() {
        super("Invalid email or password",
                ErrorType.INVALID_CREDENTIALS);
    }
}