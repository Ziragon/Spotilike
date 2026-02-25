package com.spotilike.userservice.exception.notfound;

import com.spotilike.userservice.exception.base.ErrorType;

public class UserNotFoundException extends NotFoundException {

    public UserNotFoundException(Object identifier) {
        super("User", identifier, ErrorType.USER_NOT_FOUND);
    }
}