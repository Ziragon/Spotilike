package com.spotilike.userservice.exception.notfound;

import com.spotilike.userservice.exception.base.ErrorType;

public class UserNotFoundException extends NotFoundException {

    public UserNotFoundException(Long userId) {
        super("User", userId, ErrorType.USER_NOT_FOUND);
    }

    public UserNotFoundException(String email) {
        super("User", email, ErrorType.USER_NOT_FOUND);
    }
}