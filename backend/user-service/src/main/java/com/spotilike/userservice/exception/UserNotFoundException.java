package com.spotilike.userservice.exception;

public class UserNotFoundException extends NotFoundException {

    public UserNotFoundException(Object identifier) {
        super("User", identifier, ErrorType.USER_NOT_FOUND);
    }
}